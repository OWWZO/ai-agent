import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BuiltinActionType, type ActionEvent } from '@openuidev/react-lang';

const dispatch = vi.fn();
vi.mock('@/components/genui/genUiActionBus', () => ({
  dispatchGenUiAction: (action: unknown) => dispatch(action),
}));

const { dispatchOpenUiAction } = await import('./openUiActionBridge');

const event = (partial: Partial<ActionEvent>): ActionEvent => ({
  type: BuiltinActionType.ContinueConversation,
  params: {},
  humanFriendlyMessage: '',
  ...partial,
});

describe('dispatchOpenUiAction', () => {
  beforeEach(() => dispatch.mockClear());

  it('sends @ToAssistant messages as a new turn', () => {
    dispatchOpenUiAction(event({ humanFriendlyMessage: 'Tell me more' }));

    expect(dispatch).toHaveBeenCalledWith({
      type: 'send_message',
      payload: { content: 'Tell me more' },
    });
  });

  it('appends form values and skips reactive bindings', () => {
    dispatchOpenUiAction(
      event({
        humanFriendlyMessage: 'Help me choose a bike',
        formState: { $pace: 'Relaxed', budget: 1200, tags: ['road', 'gravel'] },
      })
    );

    const action = dispatch.mock.calls[0][0];
    expect(action.type).toBe('send_message');
    expect(action.payload.content).toContain('Help me choose a bike');
    expect(action.payload.content).toContain('budget: 1200');
    expect(action.payload.content).toContain('tags: road、gravel');
    expect(action.payload.content).not.toContain('$pace');
  });

  it('ignores an empty message', () => {
    dispatchOpenUiAction(event({ humanFriendlyMessage: '   ' }));
    expect(dispatch).not.toHaveBeenCalled();
  });

  it('routes @OpenUrl through the shared open-url policy', () => {
    dispatchOpenUiAction(
      event({
        type: BuiltinActionType.OpenUrl,
        params: { url: 'https://example.com/report' },
      })
    );

    expect(dispatch).toHaveBeenCalledWith({
      type: 'open_url',
      payload: { url: 'https://example.com/report', external: true },
    });
  });

  it('ignores an open-url event without a url', () => {
    dispatchOpenUiAction(event({ type: BuiltinActionType.OpenUrl, params: {} }));
    expect(dispatch).not.toHaveBeenCalled();
  });
});
