import { cn } from "@/lib/utils";

interface LoadingSpinnerProps {
  color?: string;
  className?: string;
  children?: React.ReactNode;
  size?: "sm" | "md" | "lg";
}

const sizeMap = {
  sm: "w-4 h-4",
  md: "w-5 h-5",
  lg: "w-6 h-6",
};

const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({
  className,
  children,
  color = "white",
  size = "md",
}) => {
  return (
    <div className={cn("flex items-center gap-2", className)}>
      <div className={cn("relative shrink-0", sizeMap[size])}>
        <div
          className="cute-spinner absolute inset-0 rounded-full"
          style={{ color: color === "white" ? "var(--color-accent)" : color }}
        />
      </div>
      {children}
    </div>
  );
};

export default LoadingSpinner;
