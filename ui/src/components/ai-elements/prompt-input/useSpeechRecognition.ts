import {
  type RefObject,
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";

interface PromptInputSpeechRecognition extends EventTarget {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  start(): void;
  stop(): void;
  onstart: ((this: PromptInputSpeechRecognition, ev: Event) => any) | null;
  onend: ((this: PromptInputSpeechRecognition, ev: Event) => any) | null;
  onresult:
    | ((this: PromptInputSpeechRecognition, ev: PromptInputSpeechRecognitionEvent) => any)
    | null;
  onerror:
    | ((this: PromptInputSpeechRecognition, ev: PromptInputSpeechRecognitionErrorEvent) => any)
    | null;
}

interface PromptInputSpeechRecognitionEvent extends Event {
  results: PromptInputSpeechRecognitionResultList;
  resultIndex: number;
}

type PromptInputSpeechRecognitionResultList = {
  readonly length: number;
  item(index: number): PromptInputSpeechRecognitionResult;
  [index: number]: PromptInputSpeechRecognitionResult;
};

type PromptInputSpeechRecognitionResult = {
  readonly length: number;
  item(index: number): PromptInputSpeechRecognitionAlternative;
  [index: number]: PromptInputSpeechRecognitionAlternative;
  isFinal: boolean;
};

type PromptInputSpeechRecognitionAlternative = {
  transcript: string;
  confidence: number;
};

interface PromptInputSpeechRecognitionErrorEvent extends Event {
  error: string;
}

declare global {
  interface Window {
    SpeechRecognition: {
      new (): PromptInputSpeechRecognition;
    };
    webkitSpeechRecognition: {
      new (): PromptInputSpeechRecognition;
    };
  }
}

type UseSpeechRecognitionOptions = {
  textareaRef?: RefObject<HTMLTextAreaElement | null>;
  onTranscriptionChange?: (text: string) => void;
};

/**
 * 语音识别能力探测、事件绑定和启停控制统一放到 Hook 中。
 */
export function useSpeechRecognition(options: UseSpeechRecognitionOptions) {
  const { textareaRef, onTranscriptionChange } = options;
  const [isListening, setIsListening] = useState(false);
  const [recognition, setRecognition] = useState<PromptInputSpeechRecognition | null>(null);
  const recognitionRef = useRef<PromptInputSpeechRecognition | null>(null);

  useEffect(() => {
    if (
      typeof window !== "undefined" &&
      ("SpeechRecognition" in window || "webkitSpeechRecognition" in window)
    ) {
      const SpeechRecognitionConstructor =
        window.SpeechRecognition || window.webkitSpeechRecognition;
      const speechRecognition = new SpeechRecognitionConstructor();

      speechRecognition.continuous = true;
      speechRecognition.interimResults = true;
      speechRecognition.lang = "en-US";

      speechRecognition.onstart = () => {
        setIsListening(true);
      };

      speechRecognition.onend = () => {
        setIsListening(false);
      };

      speechRecognition.onresult = (event) => {
        let finalTranscript = "";

        for (let index = event.resultIndex; index < event.results.length; index += 1) {
          const result = event.results[index];
          if (result.isFinal) {
            finalTranscript += result[0]?.transcript ?? "";
          }
        }

        if (finalTranscript && textareaRef?.current) {
          const textarea = textareaRef.current;
          const currentValue = textarea.value;
          const nextValue =
            currentValue + (currentValue ? " " : "") + finalTranscript;

          textarea.value = nextValue;
          textarea.dispatchEvent(new Event("input", { bubbles: true }));
          onTranscriptionChange?.(nextValue);
        }
      };

      speechRecognition.onerror = (event) => {
        console.error("Speech recognition error:", event.error);
        setIsListening(false);
      };

      recognitionRef.current = speechRecognition;
      setRecognition(speechRecognition);
    }

    return () => {
      if (recognitionRef.current) {
        recognitionRef.current.stop();
      }
    };
  }, [onTranscriptionChange, textareaRef]);

  const toggleListening = useCallback(() => {
    if (!recognition) {
      return;
    }

    if (isListening) {
      recognition.stop();
    } else {
      recognition.start();
    }
  }, [isListening, recognition]);

  return {
    isListening,
    recognition,
    toggleListening,
  };
}
