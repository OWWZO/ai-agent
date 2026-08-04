import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";

import {
  featuredConversationApi,
  type FeaturedConversationDetail,
} from "@/services/featuredConversation";

import { FeaturedConversationDetailView } from "./view";

interface FeaturedConversationDetailPageProps {
  embedded?: boolean;
  featuredId?: string;
  onBack?: () => void;
}

export default function FeaturedConversationDetailPage(
  props: FeaturedConversationDetailPageProps
) {
  const params = useParams();
  const featuredId = props.featuredId || params.featuredId || "";
  const [detail, setDetail] = useState<FeaturedConversationDetail | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    // featuredId 同时支持路由参数和嵌入 props；没有 ID 时清空旧详情。
    if (!featuredId) {
      setDetail(null);
      return;
    }

    // 组件卸载或 ID 切换后忽略旧请求，避免详情闪回上一条会话。
    let disposed = false;
    setLoading(true);

    featuredConversationApi
      .detail(featuredId)
      .then((data) => {
        if (disposed) {
          return;
        }
        setDetail(data || null);
      })
      .catch((error) => {
        console.error("加载精品对话详情失败", error);
        if (disposed) {
          return;
        }
        setDetail(null);
      })
      .finally(() => {
        if (!disposed) {
          setLoading(false);
        }
      });

    return () => {
      disposed = true;
    };
  }, [featuredId]);

  return (
    <FeaturedConversationDetailView
      embedded={props.embedded}
      loading={loading}
      detail={detail}
      onBack={props.onBack}
    />
  );
}
