# coding=utf-8
import json

import copy
import os

import time
import asyncio

import jieba
import jieba.analyse

from typing import List, Dict, Any, Optional
from textwrap import dedent
from jinja2 import Template

from reactor_tool.util.llm_util import ask_llm
from reactor_tool.util.log_util import logger

from reactor_tool.util.prompt_util import get_prompt
from reactor_tool.tool.table_rag.retriever import Retriever
from reactor_tool.tool.table_rag.utils import read_json, is_numeric, desired_field_order, sort_dict_list_by_keys
from reactor_tool.tool.table_rag.table_column_filter import ColumnFilterModule

class TableAgent:
    def __init__(
        self,
        request_id,
    ):

        self.model_name = os.getenv("TR_EXTRACT_SYS_WSD_MODEL_NAME", "gpt-4o-0806")
        self.max_tokens = 4096
        self.temperature = 0
        self.top_p =  0.95
        self.retriever = Retriever(request_id)
    
    async def ask_llm(self, prompt) -> str:
        
        messages = [
            {
                "role": "system",
                "content": "you are a helpful assistant.",
            },
            {
                "role": "user",
                "content": prompt
            }
        ]
        response_text = ""
        
        async for chunk in ask_llm(messages=messages,
                                model=self.model_name,
                                stream=False,
                                temperature=self.temperature,
                                top_p=self.top_p,
                                only_content=True):
            response_text += chunk
        return response_text
    

class TableRAGAgent(TableAgent):
    def __init__(self,
                 request_id,
                 query="",
                 modelCodeList=[],
                 current_date_info="",
                 schema_info=[],
                 user_info="",
                 use_elastic=False,
                 use_vector=False,
                 **kwargs):
        super(TableRAGAgent, self).__init__(request_id)
        self.request_id = request_id

        self.jieba_query_map = {}
        self.model_code_list = modelCodeList
        self.query = query
        
        self.model_code_topk = None
        self.schema_topk = None
        
        self.schema_info = schema_info
        self.user_info = user_info
        self.current_date_info = current_date_info
        
        self.use_elastic = use_elastic
        self.use_vector = use_vector
        
        self.schema_list_max_length = os.getenv("TABLE_RAG_SCHEMA_LIST_MAX_LENGTH", 200)
        self.business_prompt_max_length = os.getenv("TABLE_RAG_BUSINESS_PROMPT_MAX_LENGTH", 1500)
        self.use_prompt_max_length = os.getenv("TABLE_RAG_USE_PROMPT_MAX_LENGTH", 500)
    
    def filter_queries(self, cell_queries):
        cell_queries = [cell for cell in cell_queries if not is_numeric(cell)]
        cell_queries = list(set(cell_queries))
        return cell_queries
    
    async def get_jieba_queries(self, query):
        start_time = time.time()
        column_queries = []
        if query not in self.jieba_query_map:
            
            # 过滤虚词，保留非虚词
            allowPOS = ('n', 'nr', 'ns', 'nt', 'nz', 'v', 'vn', 'a', 'an')
            column_queries = jieba.analyse.extract_tags(query, topK=20, withWeight=False, allowPOS=allowPOS)
            column_queries = list(column_queries) + [query]
            
            logger.info(
                f"sn: {self.request_id} get_jieba_queries: {len(column_queries)} jieba queries: {column_queries}")
            column_queries = self.filter_queries(column_queries)
            logger.info(
                f"sn: {self.request_id} get_jieba_queries: {len(column_queries)} filter_queries: {column_queries}")
            self.jieba_query_map[query] = copy.deepcopy(column_queries)
        
        else:
            column_queries = self.jieba_query_map[query]
        
        duration = round(time.time() - start_time, 4)
        logger.info(
            f"sn: {self.request_id} [get_jieba_queries] duration: {duration} seconds column_queries: {column_queries}")
        return column_queries
    
    async def retrieve_cell_by_jieba(self, query, model_code_list):
        column_queries = await self.get_jieba_queries(query)
        retrieved_docs = await self.retrieve_cell_concurrent(column_queries, model_code_list)
        return retrieved_docs
    
    async def retrieve_schema_by_jieba(self, query, model_code_list):
        start_time = time.time()
        column_queries = await self.get_jieba_queries(query)
        retrieved_docs = await self.retrieve_schemas_concurrent(column_queries, model_code_list=model_code_list)
        duration = round(time.time() - start_time, 3)
        logger.info(
            f"sn: {self.request_id} [retrieve_schema_by_jieba] duration: {duration} seconds with len(retrieved_docs): {len(retrieved_docs['retrieved_docs'])}")
        
        return retrieved_docs
    
    async def retrieve_schemas_concurrent(
        self,
        columns: List[str],
        model_code_list: Optional[List[str]] = None,
        max_concurrent: int = 10,  # 控制最大并发数
        timeout: float = 30000.0
    ) -> dict[Any]:
        """
        异步并发检索 schema 信息，支持去重、超时、异常隔离、并发控制。

        Args:
            columns: 列名或语义查询列表
            model_code_list: 可选模型/表白名单
            max_concurrent: 最大并发请求数
            timeout: 整体超时时间（秒）

        Returns:
            合并后的 schema 列表
        """
        start_time = time.time()
        retrieved_docs: List[Any] = []
        success_count = 0
        failure_count = 0
        
        if not columns:
            logger.debug(f"sn: {self.request_id} [retrieve_schemas_concurrent] columns is empty, skipped.")
            return {"retrieved_docs": []}
        
        # 🔁 去重，保持顺序
        seen = set()
        unique_columns = [col for col in columns if not (col in seen or seen.add(col))]
        total_columns = len(unique_columns)
        
        # 🛑 可选：使用信号量限制并发数
        semaphore = asyncio.Semaphore(max_concurrent)
        
        async def fetch_schema_with_limit(col: str) -> List[Any]:
            async with semaphore:
                try:
                    # 单任务超时 10s
                    result = await asyncio.wait_for(
                        self.retriever.retrieve_schema(query=col, model_code_list=model_code_list),
                        timeout=100000.0
                    )
                    return result or []
                except asyncio.TimeoutError:
                    logger.warning(
                        f"sn: {self.request_id} [Schema Retrieve Timeout] "
                        f"column='{col}' timed out after 10s"
                    )
                    return []
        # 🚀 创建所有任务
        tasks = [fetch_schema_with_limit(col) for col in unique_columns]
        
        try:
            # ⏱️ 整体超时控制
            results = await asyncio.wait_for(
                asyncio.gather(*tasks, return_exceptions=False),
                timeout=timeout
            )
            
            # 📦 合并结果
            for result in results:
                if result:  # 非空列表
                    if isinstance(result, dict) and "data" in result:
                        retrieved_docs.extend(result["data"])
                    else:
                        logger.warning(f"Unexpected result format or None: {result}")
                        retrieved_docs.extend([])
                    success_count += 1
                else:
                    failure_count += 1
        
        except asyncio.TimeoutError:
            logger.error(
                f"sn: {self.request_id} [Schema Retrieve Timeout] "
                f"Overall timeout after {timeout}s, returning partial results."
            )
        # 📊 统计日志
        duration = time.time() - start_time
        logger.info(
            f"sn: {self.request_id} [Table RAG] [retrieve_schemas_concurrent] "
            f"Completed in {duration:.4f}s | "
            f"Success: {success_count}, Failed: {failure_count}, Total: {total_columns} | "
            f"Retrieved {len(retrieved_docs)} schema items | "
            f"model_code_list: {json.dumps(model_code_list, ensure_ascii=False, indent=2)}"
        )
        
        return {"retrieved_docs": retrieved_docs}
    
    async def retrieve_cell_concurrent(
        self,
        queries: List[str],
        model_code_list: Optional[List[str]] = None,
        max_concurrent: int = 10,  # 控制最大并发数（可选信号量）
        timeout: float = 30000.0
    ) -> Dict[str, Any]:
        """
        异步并发检索单元格信息，基于 asyncio.gather，支持超时、去重、异常隔离。

        Args:
            queries: 查询列表（如列名）
            model_code_list: 模型/表白名单
            max_concurrent: 最大并发数（使用信号量控制，可选）
            timeout: 整体超时时间（秒）

        Returns:
            合并后的字典 {key: value}
        """
        start_time = time.time()
        retrieved_docs: Dict[str, Any] = {}
        success_count = 0
        failure_count = 0
        
        if not queries:
            return retrieved_docs
        
        # 🔁 去重，保持顺序
        seen = set()
        unique_queries = [q for q in queries if not (q in seen or seen.add(q))]
        
        # 🛑 可选：使用信号量限制最大并发数（防止压垮下游）
        semaphore = asyncio.Semaphore(max_concurrent)
        
        async def fetch_with_limit(query: str) -> Dict[str, Any]:
            async with semaphore:
                try:
                    # 单任务设置超时（避免某个卡住）
                    result = await asyncio.wait_for(
                        self.retriever.retrieve_cell(query=query, model_code_list=model_code_list),
                        timeout=100000.0
                    )
                    return result or {}
                except asyncio.TimeoutError:
                    logger.warning(
                        f"sn: {self.request_id} [Cell Retrieve Timeout] "
                        f"query='{query}' timed out after 10s"
                    )
                    return {}
                except Exception as e:
                    logger.warning(
                        f"sn: {self.request_id} [Cell Retrieve Failed] "
                        f"query='{query}', error={type(e).__name__}: {e}"
                    )
                    return {}
        
        # 🚀 创建所有异步任务
        tasks = [fetch_with_limit(query) for query in unique_queries]
        
        try:
            # ⏱️ 整体超时控制
            results = await asyncio.wait_for(
                asyncio.gather(*tasks, return_exceptions=False),
                timeout=timeout
            )
            
            # 📦 合并结果
            for result in results:
                if isinstance(result, dict) and result:
                    data = result.get("data", {})
                    retrieved_docs.update(data)
                    success_count += 1
                else:
                    failure_count += 1
        
        except asyncio.TimeoutError:
            logger.error(
                f"sn: {self.request_id} [Cell Retrieve Timeout] "
                f"Overall timeout after {timeout}s, returning partial results if any."
            )
            # 注意：gather 超时后，未完成的任务仍在后台运行（可接受）
            # 如需取消，需使用更复杂的 cancel 逻辑
        except Exception as e:
            logger.error(
                f"sn: {self.request_id} [Cell Retrieve Unexpected Error] "
                f"{type(e).__name__}: {e}"
            )
            failure_count += len(unique_queries)  # 假设全部失败
        
        # 📊 统计日志
        duration = time.time() - start_time
        logger.info(
            f"sn: {self.request_id} [Table RAG] [retrieve_cell_concurrent] "
            f"Completed in {duration:.4f}s | "
            f"Success: {success_count}, Failed: {failure_count}, Queries: {unique_queries} | "
            f"Retrieved {len(retrieved_docs)} entries | "
            f"model_code_list: {model_code_list}"
        )
        
        return {"retrieved_docs": retrieved_docs}
    
    async def retrieve_schema_by_prompt(self, prompt, max_attempt=3, model_code_list=[], query=""):
        column_queries = []
        for _ in range(max_attempt):
            text = await self.ask_llm(prompt=prompt)
            try:
                text = text[text.find('['):text.find(']') + 1]
                column_queries = read_json(text)
                logger.info(
                    f"sn: {self.request_id} [Table RAG] [retrieve_schema_by_prompt] prompt_schema_queries {column_queries}")
                assert isinstance(column_queries, list)
                break
            except Exception as e:
                logger.info(f'sn: {self.request_id} ### Schema Retrieval Error:', text)
                column_queries = []
        
        column_queries = self.filter_queries(column_queries)
        
        retrieved_docs = await self.retrieve_schemas_concurrent(column_queries, model_code_list=model_code_list)
        return retrieved_docs
    
    async def retrieve_schema_by_question(self, question, model_code_list):
        data = await self.retriever.retrieve_schema(question, model_code_list)
        
        return {"retrieved_docs": data}
    
    async def retrieve_cell_by_prompt(self, prompt, max_attempt=3, model_code_list=[]):
        for _ in range(max_attempt):
            text = await self.ask_llm(prompt)
            try:
                text = text[text.find('['):text.find(']') + 1]
                cell_queries = read_json(text)
                assert isinstance(cell_queries, list)
                break
            except Exception as e:
                cell_queries = []
                logger.info(f'sn: {self.request_id} ### Cell Retrieval Error:', text)
        cell_queries = self.filter_queries(cell_queries)
        # dedup
        cell_queries = list(set(cell_queries))
        
        retrieved_docs = await self.retrieve_cell_concurrent(cell_queries, model_code_list=model_code_list)
        return retrieved_docs
    
    async def retrieve_cell_by_question(self, question, model_code_list):
        _data = await self.retriever.retrieve_cell(question, model_code_list=model_code_list)
        data = _data.get("data", {})
        
        return {"retrieved_docs": data}
    
    def get_table_caption(self, model_code_list, schema_info):
        
        if schema_info is None or len(schema_info) == 0:
            return model_code_list, ""
        
        model_code2model_description = {item["modelCode"]:
                                            "数据表名：" + item["modelName"] + "\n"
                                                                              "数据表描述" + item["usePrompt"][
                                                :self.use_prompt_max_length] + "\n"
                                                                               "业务规则" + item["businessPrompt"][
                                                :self.business_prompt_max_length] + "\n"
                                        for item in schema_info}
        
        # table caption recall
        table_caption = ""
        for model_code in model_code_list:
            model_description = model_code2model_description.get(model_code, "")
            table_caption += dedent(model_description) + "\n"
        
        return model_code_list, table_caption
    
    def all_table_schema_list2model_code_schema(self, all_table_schema_list, model_code_topk, schema_topk):
        schema_topk = 100000 if schema_topk is None else schema_topk
        model_code_topk = 100000 if model_code_topk is None else model_code_topk
        
        model_code_schema_map = {}  # 临时用于分组和计算
        # 把每一个list 里面的dict 按 column 排序
        all_table_schema_list = sort_dict_list_by_keys(all_table_schema_list, desired_field_order)
        # 1. 按 modelCode 分组，累加 score，收集 schema
        for schema in all_table_schema_list:
            model_code = schema["modelCode"]
            score = schema.get("score", 0)
            
            if model_code not in model_code_schema_map:
                model_code_schema_map[model_code] = {
                    "modelCode": model_code,
                    "schemaList": [],
                    "score": 0.0,
                }
            
            model_code_schema_map[model_code]["schemaList"].append(schema)
            model_code_schema_map[model_code]["score"] += score
        
        # 2. 对每个表的 schemaList 按 score 降序排列，并截取前 schema_topk 个
        for item in model_code_schema_map.values():
            item["schemaList"] = sorted(
                item["schemaList"],
                key=lambda x: x.get("score", 0),
                reverse=True
            )[:schema_topk]
        
        # 3. 所有表按总分排序，取 top model_code_topk 个表名
        top_model_codes = sorted(
            model_code_schema_map.keys(),
            key=lambda k: model_code_schema_map[k]["score"],
            reverse=True
        )[:model_code_topk]
        
        # 4. 构造最终结果：只保留 topK 表，按 modelCode 为 key 的字典
        result = [
            model_code_schema_map[model_code]
            for model_code in top_model_codes
        ]
        
        return result
    
    async def choose_schema(self, query, model_code_list=[], table_caption=""):
        # Extract column names
        retrieved_columns = []
        retrieved_cells = {}
        
        if not self.use_vector and not self.use_elastic:
            logger.error(f"sn: {self.request_id} 当不使用向量和es检索内容时，无法有效实现RAG召回内容")
        
        keywords = await self.get_jieba_queries(query)
        
        if self.use_vector:
            # query 拆解 扩召回
            _retrieved_docs = await self.retrieve_schema_by_jieba(query, model_code_list=model_code_list)
            retrieved_columns.extend(_retrieved_docs.get("retrieved_docs", []))
            
            table_rag_prompts = get_prompt("table_rag")
            prompt = Template(table_rag_prompts["extract_column_prompt"]) \
                .render(table_caption=table_caption, query=query, keywords=keywords)
            
            _retrieved_docs = await self.retrieve_schema_by_prompt(prompt, model_code_list=model_code_list, query=query)
            
            retrieved_columns.extend(_retrieved_docs.get("retrieved_docs", []))
            
            retrieved_columns = self.retriever.qd_merge_rerank(retrieved_columns)
        
        if self.use_elastic:
            _retrieved_docs = await self.retrieve_cell_by_jieba(query, model_code_list=model_code_list)
            logger.debug(f"sn: {self.request_id} [retrieve_cell_by_jieba] _retrieved_docs {_retrieved_docs} ")
            retrieved_cells.update(_retrieved_docs.get("retrieved_docs", {}))
            
            table_rag_prompts = get_prompt("table_rag")
            prompt = Template(table_rag_prompts["extract_cell_prompt"]) \
                .render(table_caption=table_caption, query=query, keywords=keywords)
            
            _retrieved_docs = await self.retrieve_cell_by_prompt(prompt, model_code_list=model_code_list)
            retrieved_cells.update(_retrieved_docs.get("retrieved_docs", {}))

        # merge schema and cells
        update_schema_few_shot = self.retriever.qd_es_merge(retrieved_cells, retrieved_columns)
        
        # split model code and schema
        model_code_schema_result = self.all_table_schema_list2model_code_schema(
            all_table_schema_list=update_schema_few_shot, model_code_topk=self.model_code_topk,
            schema_topk=self.schema_topk)
        
        return model_code_schema_result
    
    async def run_recall(self, query):
        start_time = time.time()
        result = []
        model_code_list = self.model_code_list
        # choose table
        model_code_list, table_caption = self.get_table_caption(model_code_list, self.schema_info)
        
        choosed_schema = await self.choose_schema(query, model_code_list,
                                                  table_caption)
        
        return {"choosed_schema": choosed_schema}
    
    async def run(self, query):
        model_code_list = self.model_code_list
        model_code_list, table_caption = self.get_table_caption(model_code_list, self.schema_info)
        
        choosed_schema = await self.choose_schema(query, model_code_list,
                                                  table_caption)
        
        model_info_list2model_info_map = {
            model_info["modelCode"]: model_info for model_info in self.schema_info
        }
        choosed_schema_info_list = []
        for choosed_ in choosed_schema:
            _model_code = choosed_["modelCode"]
            choosed_schema_list = choosed_["schemaList"]
            
            model_info_list2model_info_map[_model_code]["schemaList"] = choosed_schema_list
            choosed_schema_info = model_info_list2model_info_map[_model_code]
            choosed_schema_info_list.append(choosed_schema_info)
        
        self.schema_filter_module = ColumnFilterModule(
            request_id=self.request_id,
            query=self.query,
            current_date_info=self.current_date_info,
            table_id_list=model_code_list,
            column_info= choosed_schema_info_list,
        )
        
        filter_schema = await self.schema_filter_module.batch_get_result()
        filter_schema = [item for item in filter_schema if item is not None]
        return {"choosed_schema": filter_schema}

