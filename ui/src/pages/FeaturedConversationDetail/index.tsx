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
    if (!featuredId) {
      setDetail(null);
      return;
    }

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
