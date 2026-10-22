"""
向量数据库接口模块

该模块提供向量数据库操作的统一接口：
- 向量的增删改查
- 批量向量操作
- 相似度检索
- 索引管理
- 集合（Collection）管理

主要功能：
1. 向量数据库连接和配置
2. 向量数据的CRUD操作
3. 高效的批量插入
4. 向量相似度搜索
5. 过滤条件查询
6. 混合搜索（文本+图像+页面）
"""

from typing import List, Dict, Tuple, Optional
import threading

from .config_manager import VectorStoreConfig
from .vector_store_factory import VectorStoreFactory
from ..utils.logger_utils import logger


class VectorStore:
    """
    向量存储统一接口类

    提供对文本、图像和页面向量存储的统一访问接口，使用懒加载模式。
    
    使用线程安全的单例模式实现，确保全局只有一个实例。
    """
    
    _instance = None
    _lock = threading.Lock()

    def __new__(cls, store_type: Optional[str] = None):
        """
        单例模式实现 - 创建或返回现有实例
        
        Args:
            store_type: 存储类型，如果为 None 则从配置中读取
            
        Returns:
            VectorStore: 单例实例
        """
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    cls._instance = super(VectorStore, cls).__new__(cls)
                    cls._instance._initialized = False
        return cls._instance

    def __init__(self, store_type: Optional[str] = None):
        """
        初始化向量存储 - 只在第一次创建时执行

        Args:
            store_type: 存储类型，如果为 None 则从配置中读取
        """
        # 确保只初始化一次
        if self._initialized:
            return
            
        self.config = VectorStoreConfig()
        self.store_type = store_type or self.config.store_type

        # 懒加载属性
        self._base_store = None
        self._text_store = None
        self._image_store = None
        self._page_store = None
        
        self._initialized = True

        # 单例只固定一次配置和连接工厂；后续调用即使传入不同 store_type，也必须复用同一索引视图。
        logger.debug(f"VectorStore initialized with type: {self.store_type}")

    @property
    def image_vector_enabled(self) -> bool:
        """统一暴露图片向量开关，避免业务层直接感知配置对象细节。"""
        return self.config.image_vector_enabled

    def _build_disabled_query_result(self, query_vectors: Optional[List[list[float]]]):
        if not query_vectors:
            return []
        return [[] for _ in query_vectors]

    @property
    def base_store(self):
        """基础向量数据库实例（懒加载）"""
        if self._base_store is None:
            self._base_store = VectorStoreFactory.create_vector_store(self.store_type)
            logger.info(f"Created base vector store: {self.store_type}")
        return self._base_store

    @property
    def text_store(self):
        """文本向量存储实例（懒加载）"""
        if self._text_store is None:
            logger.info("Init text store")
            self._text_store = VectorStoreFactory.create_text_store(
                collection_name=self.config.text_collection,
                vector_size=self.config.text_dimension,
                store_type=self.store_type,
                vector_store=self.base_store
            )
            self._ensure_collection_ready(self._text_store, self.config.text_collection)
            logger.debug("Created text vector store")
        return self._text_store

    @property
    def image_store(self):
        """图像向量存储实例（懒加载）"""
        if not self.image_vector_enabled:
            raise RuntimeError(f"MRAG 图片向量集合已关闭，当前模式: {self.config.image_index_mode}")
        if self._image_store is None:
            self._image_store = VectorStoreFactory.create_image_store(
                collection_name=self.config.image_collection,
                vector_size=self.config.image_dimension,
                index_fields=["kb_id", "doc_id", "image_id"],
                store_type=self.store_type,
                vector_store=self.base_store
            )
            self._ensure_collection_ready(self._image_store, self.config.image_collection)
            logger.debug("Created image vector store")
        return self._image_store

    @property
    def page_store(self):
        """页面向量存储实例（懒加载）"""
        if not self.image_vector_enabled:
            raise RuntimeError(f"MRAG 页面向量集合已关闭，当前模式: {self.config.image_index_mode}")
        if self._page_store is None:
            self._page_store = VectorStoreFactory.create_image_store(
                collection_name=self.config.page_collection,
                vector_size=self.config.page_dimension,
                index_fields=["kb_id", "page_id", "doc_id", "chunk_id"],
                store_type=self.store_type,
                vector_store=self.base_store
            )
            self._ensure_collection_ready(self._page_store, self.config.page_collection)
            logger.debug("Created page vector store")
        return self._page_store

    def _ensure_collection_ready(self, store, collection_name: str):
        """首次获取集合包装器时，顺手保证底层 collection 已就绪。"""
        # 集合初始化绑定到懒加载入口，保证文本、图片、页面三类索引只有被实际使用时才创建。
        try:
            store.create_collection()
            logger.info(f"向量集合已就绪: {collection_name}")
        except Exception as e:
            logger.error(f"初始化向量集合失败: {collection_name}, error={e}")
            raise

    # ==================== 集合管理 ====================

    def create_all_collections(self):
        """创建所有向量集合"""
        try:
            self.text_store.create_collection()
            logger.info("文本向量集合创建成功")
        except Exception as e:
            logger.error(f"文本向量集合创建失败: {e}")

        if not self.image_vector_enabled:
            logger.info("MRAG 图片向量集合已关闭，跳过 image/page collection 初始化")
            return

        try:
            self.image_store.create_collection()
            logger.info("图像向量集合创建成功")
        except Exception as e:
            logger.error(f"图像向量集合创建失败: {e}")

        try:
            self.page_store.create_collection()
            logger.info("页面向量集合创建成功")
        except Exception as e:
            logger.error(f"页面向量集合创建失败: {e}")

    # ==================== 添加向量 ====================

    def add_text_chunks(self, text_chunks: List[Dict]):
        """
        添加文本块到向量数据库

        Args:
            text_chunks: 文档块列表，每个块包含以下字段：
                - kb_id: 知识库ID (str)
                - doc_id: 文档ID (str)
                - chunk_id: 文档块ID (str)
                - page_no: 页码 (int, Optional)
                - page_chunk_id: 页内块ID (int, Optional)
                - text: 文本内容 (str)
                - vector: 向量数据 (List[float])
                - sparse_vector: 稀疏向量数据 (Dict[str, int])
        """
        return self.text_store.add_docs(text_chunks)

    def add_image_chunks(self, image_chunks: List[Dict]):
        """
        添加图像块到向量数据库

        Args:
            image_chunks: 图像块列表，每个块包含以下字段：
                - kb_id: 知识库ID (str)
                - doc_id: 文档ID (str)
                - image_id: 图像ID (str)
                - vector: 向量数据 (List[float])
        """
        if not self.image_vector_enabled:
            logger.info("MRAG 图片向量集合已关闭，跳过 image chunk 入库")
            return True
        return self.image_store.add_images(image_chunks)

    def add_page_chunks(self, page_chunks: List[Dict]):
        """
        添加页面块到向量数据库

        Args:
            page_chunks: 页面块列表，每个块包含以下字段：
                - kb_id: 知识库ID (str)
                - page_id: 页面ID (str)
                - doc_id: 文档ID (str)
                - chunk_id: 块ID (str)
                - vector: 向量数据 (List[float])
        """
        if not self.image_vector_enabled:
            logger.info("MRAG 页面向量集合已关闭，跳过 page chunk 入库")
            return True
        return self.page_store.add_images(page_chunks)

    # ==================== 搜索向量 ====================

    def search_text_vector(self,
                           query_vectors: List[list[float]],
                           limit: int = 10,
                           score_threshold: float = 0.0,
                           filter_conditions: Optional[Dict] = None) -> list[list[dict]]:
        """
        搜索文本向量

        Args:
            query_vectors: 查询向量
            limit: 返回结果数量限制
            score_threshold: 相似度阈值
            filter_conditions: 过滤条件，如 {"kb_id": 1, "doc_id": 2}

        Returns:
            List[Dict]: 搜索结果列表，每个结果包含id, score, payload等
        """
        return self.text_store.search_vector(
            query_vectors=query_vectors,
            limit=limit,
            score_threshold=score_threshold,
            filter_conditions=filter_conditions
        )

    def search_image_vector(self,
                            query_vectors: List[list[float]],
                            limit: int = 10,
                            score_threshold: float = 0.0,
                            filter_conditions: Optional[Dict] = None):
        """
        搜索图像向量

        Args:
            query_vectors: 查询向量
            limit: 返回结果数量限制
            score_threshold: 相似度阈值
            filter_conditions: 过滤条件

        Returns:
            List[Dict]: 搜索结果列表
        """
        if not self.image_vector_enabled:
            logger.info("MRAG 图片向量集合已关闭，跳过 image vector 检索")
            return self._build_disabled_query_result(query_vectors)
        return self.image_store.search_vector(
            query_vectors=query_vectors,
            limit=limit,
            score_threshold=score_threshold,
            filter_conditions=filter_conditions
        )

    def keyword_search(self,
                       queries: List[str],
                       sparse_vectors: Optional[List[Dict]] = None,
                       limit: int = 10,
                       score_threshold: float = 0.0,
                       filter_conditions: Optional[Dict] = None):
        """
        关键词搜索

        Args:
            queries: 查询关键词
            sparse_vectors: 稀疏向量查询
            limit: 返回结果数量限制
            score_threshold: 相似度阈值
            filter_conditions: 过滤条件

        Returns:
            List[Dict]: 搜索结果列表
        """
        return self.text_store.keyword_search(
            queries=queries,
            sparse_vectors=sparse_vectors,
            limit=limit,
            score_threshold=score_threshold,
            filter_conditions=filter_conditions
        )

    def search_page_vector(self,
                           query_vectors: List[list[float]],
                           limit: int = 10,
                           score_threshold: float = 0.0,
                           filter_conditions: Optional[Dict] = None):
        """
        搜索页面向量

        Args:
            query_vectors: 查询向量
            limit: 返回结果数量限制
            score_threshold: 相似度阈值
            filter_conditions: 过滤条件

        Returns:
            List[Dict]: 搜索结果列表
        """
        if not self.image_vector_enabled:
            logger.info("MRAG 页面向量集合已关闭，跳过 page vector 检索")
            return self._build_disabled_query_result(query_vectors)
        return self.page_store.search_vector(
            query_vectors=query_vectors,
            limit=limit,
            score_threshold=score_threshold,
            filter_conditions=filter_conditions
        )

    # ==================== 删除向量 ====================
    def delete_text_by_kb_id(self, kb_id: str):
        """根据知识库ID删除文本向量"""
        return self.text_store.delete_by_kb_id(kb_id)

    def delete_image_by_kb_id(self, kb_id: str):
        """根据知识库ID删除图像向量"""
        if not self.image_vector_enabled:
            return True
        return self.image_store.delete_by_kb_id(kb_id)

    def delete_page_by_kb_id(self, kb_id: str):
        """根据知识库ID删除页面向量"""
        if not self.image_vector_enabled:
            return True
        return self.page_store.delete_by_kb_id(kb_id)

    def delete_by_file_ids(self, kb_id: str, file_ids: List[str]):
        self.text_store.delete_by_file_ids(kb_id, file_ids)
        if self.image_vector_enabled:
            self.image_store.delete_by_file_ids(kb_id, file_ids)
            self.page_store.delete_by_file_ids(kb_id, file_ids)

    def scroll_text_payloads(self, kb_id: str, limit: int = 100, offset: Optional[str] = None):
        """滚动读取文本 payload。"""
        return self.text_store.scroll_payloads(kb_id=kb_id, limit=limit, offset=offset)

    def scroll_image_payloads(self, kb_id: str, limit: int = 100, offset: Optional[str] = None):
        """滚动读取图片 payload。"""
        if not self.image_vector_enabled:
            return [], None
        return self.image_store.scroll_payloads(kb_id=kb_id, limit=limit, offset=offset)

    def scroll_page_payloads(self, kb_id: str, limit: int = 100, offset: Optional[str] = None):
        """滚动读取页面 payload。"""
        if not self.image_vector_enabled:
            return [], None
        return self.page_store.scroll_payloads(kb_id=kb_id, limit=limit, offset=offset)
