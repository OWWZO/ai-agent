import Dot from "./Dot";
import LoadingSpinner from "../LoadingSpinner";

export const getStatusIcon = (status?: CHAT.PlanStatus) => {
  switch (status) {
    case 'not_started':
      return <Dot className="animate-pulse" />;
    case 'in_progress':
      return <LoadingSpinner />;
    case 'completed':
      return <i className="font_family icon-yiwanchengtianchong text-primary"></i>;
    default:
      return null;
  }
};