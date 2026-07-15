import { useEffect, useState } from "react";

import FeaturedConversationDetailPage from "@/pages/FeaturedConversationDetail";
import {
  featuredConversationApi,
  type FeaturedConversationPage,
} from "@/services/featuredConversation";

import { FeaturedConversationsView } from "./view";

const PAGE_SIZE = 20;
const EMPTY_PAGE: FeaturedConversationPage = {
  total: 0,
  list: [],
};

interface FeaturedConversationsPageProps {
  embedded?: boolean;
  initialFeaturedId?: string;
}

export default function FeaturedConversationsPage(props: FeaturedConversationsPageProps) {
  const { embedded, initialFeaturedId = "" } = props;
  const [pageNo, setPageNo] = useState(1);
  const [page, setPage] = useState<FeaturedConversationPage>(EMPTY_PAGE);
  const [loading, setLoading] = useState(false);
  const [selectedFeaturedId, setSelectedFeaturedId] = useState(initialFeaturedId);

  useEffect(() => {
    setSelectedFeaturedId(initialFeaturedId);
  }, [initialFeaturedId]);

  useEffect(() => {
    let disposed = false;
    setLoading(true);

    featuredConversationApi
      .list({
        pageNo,
        pageSize: PAGE_SIZE,
      })
      .then((data) => {
        if (disposed) {
          return;
        }
        setPage(data || EMPTY_PAGE);
      })
      .catch((error) => {
        console.error("加载精品对话列表失败", error);
        if (disposed) {
          return;
        }
        setPage(EMPTY_PAGE);
      })
      .finally(() => {
        if (!disposed) {
          setLoading(false);
        }
      });

    return () => {
      disposed = true;
    };
  }, [pageNo]);

  if (embedded && selectedFeaturedId) {
    return (
      <FeaturedConversationDetailPage
        embedded
        featuredId={selectedFeaturedId}
        onBack={() => setSelectedFeaturedId("")}
      />
    );
  }

  return (
    <FeaturedConversationsView
      embedded={embedded}
      page={page}
      loading={loading}
      pageNo={pageNo}
      pageSize={PAGE_SIZE}
      onPageChange={setPageNo}
      onSelectCard={
        embedded
          ? (featuredId) => setSelectedFeaturedId(featuredId)
          : undefined
      }
    />
  );
}
