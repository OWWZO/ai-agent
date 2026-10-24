import { useState } from 'react';

import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { ZoomableImage } from '@/components/ui/zoomable-image';
import { cn } from '@/lib/utils';
import { Expand } from 'lucide-react';

/** 终答 Markdown 内嵌图：点击放大预览 */
export const MarkdownImagePreview: CuteType.FC<{
  src: string;
  alt?: string;
}> = ({ src, alt }) => {
  if (!src) {
    return null;
  }
  return (
    <span className="markdown-media-image-root">
      <ZoomableImage
        src={src}
        alt={alt || '图片'}
        className="markdown-media-image"
        style={{
          maxWidth: 'min(360px, 100%)',
          maxHeight: 280,
          width: 'auto',
          height: 'auto',
          objectFit: 'contain',
          borderRadius: '0.65rem',
          cursor: 'zoom-in'
        }}
      />
    </span>
  );
};

/** 终答 Markdown 内嵌音视频：内联播放 + 放大预览 Modal */
export const MarkdownMediaPlayer: CuteType.FC<{
  kind: 'video' | 'audio';
  src: string;
  title?: string;
}> = ({ kind, src, title }) => {
  const [open, setOpen] = useState(false);
  if (!src) {
    return null;
  }

  return (
    <>
      <div className="markdown-media-player-wrap">
        {kind === 'video' ? (
          <video
            className="markdown-media-video"
            src={src}
            controls
            preload="metadata"
            title={title}
          />
        ) : (
          <audio
            className="markdown-media-audio"
            src={src}
            controls
            preload="metadata"
            title={title}
          />
        )}
        <button
          type="button"
          className="markdown-media-expand-btn"
          title="放大预览"
          aria-label="放大预览"
          onClick={() => setOpen(true)}
        >
          <Expand className="h-3.5 w-3.5" />
        </button>
      </div>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent
          className={cn(
            "markdown-media-preview-modal",
            kind === "video"
              ? "max-w-[min(920px,96vw)]"
              : "max-w-[min(520px,94vw)]"
          )}
        >
          <DialogHeader>
            <DialogTitle>
              {title || (kind === "video" ? '视频预览' : '音频预览')}
            </DialogTitle>
          </DialogHeader>
          {open ? (
            kind === 'video' ? (
              <video
                className="markdown-media-video-preview"
                src={src}
                controls
                autoPlay
                preload="metadata"
              />
            ) : (
              <audio
                className="markdown-media-audio-preview"
                src={src}
                controls
                autoPlay
                preload="metadata"
              />
            )
          ) : null}
        </DialogContent>
      </Dialog>
    </>
  );
};
