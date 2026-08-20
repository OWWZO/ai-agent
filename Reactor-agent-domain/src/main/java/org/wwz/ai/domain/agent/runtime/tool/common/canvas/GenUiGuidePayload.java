package org.wwz.ai.domain.agent.runtime.tool.common.canvas;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按需返回高频 GenUI 子集的协议与布局指南。
 *
 * 该类维护的是给模型阅读的稳定提示数据，而不是运行时组件注册表；组件能力的
 * 真正校验仍由 list_ui_components 和 emit_ui_tree/patch 的执行链负责。
 */
public final class GenUiGuidePayload {

    private GenUiGuidePayload() {
    }

    public static Map<String, Object> payload() {
        Map<String, Object> guide = new LinkedHashMap<>();
        // 按 wire format、调用顺序、路由和视觉约束组织，减少模型在不同阶段混用字段。
        guide.put("purpose",
                "Ship polished, scannable gen UI with clear hierarchy — not noisy decoration.");
        guide.put("wire_format_and_syntax", List.of(
                "Envelope: {\"schemaVersion\":\"1\",\"root\":{...}} or bare root {kind,props,children}.",
                "Node keys: kind, optional props, optional children, optional nodeId.",
                "All component fields live under props. children holds only nested nodes.",
                "kind must match list_ui_components exactly (PascalCase).",
                "emit_ui_tree args: {tree, optional canvas_id}. Prefer tree as nested JSON object.",
                "emit_ui_patch: {patches:[{op,path,value?}], optional seq, canvas_id}. Paths are RFC6901 into the tree.",
                "Interactive (in-UI first): props.action = {type, payload}. Types: patch_ui|submit_form|open_url|navigate|send_message.",
                "Default: keep interaction inside GenUI. Prefer patch_ui for local UI updates (tabs, toggles, counters, visibility).",
                "patch_ui payload: {patches:[{op,path,value?}]} — applied client-side on the current tree; no chat turn.",
                "submit_form: wrap fields in Form; each field needs props.name; values stay in-UI (not sent as chat).",
                "send_message payload: {content:string} — ONLY when the Agent must reply; injects a normal user chat turn.",
                "Bare actionId string alone does NOT send chat; use explicit type send_message when needed."
        ));
        guide.put("workflow_order", List.of(
                "1. Confirm GenUI is appropriate (charts, KPI, multi-card, dashboards — not plain Q&A).",
                "2. Call list_ui_components for exact kinds/props.",
                "3. Build a minimal valid tree using wire_format_and_syntax.",
                "4. emit_ui_tree; use emit_ui_patch for small server-side follow-ups (re-renders the same final GenUI).",
                "5. For clickable controls, set Button props.action to patch_ui (local) or send_message (needs Agent).",
                "6. NEVER use canvas_publish for a simple pie/bar/line chart — Chart kind renders interactive ECharts."
        ));
        guide.put("canvas_routing", List.of(
                "Prose / Q&A / bullets → markdown (no tools).",
                "Charts / KPI / dashboards / multi-card / data grids → emit_ui_tree.",
                "Teaching / formula demos with drag sliders (Pythagoras, circle, functions) → ParametricLab or PythagorasLab (NOT a static chart).",
                "Concept / knowledge animation (playable steps, flow, layering, formula transform) → ConceptDemo or AnimStepLab.",
                "Structured 3D (geometry/color/particles) → emit_ui_tree ThreeJsFrame.",
                "Existing glb/gltf URL → emit_ui_tree Model3D (src required).",
                "Free-form WebGL HTML → canvas_publish (window.THREE preloaded on bare pages).",
                "Hosted webpage / landing / printable HTML report / page-scale layout → canvas_publish(mode=html).",
                "If markdown is enough, stay in markdown; offer GenUI only when visual layout earns it."
        ));
        guide.put("teaching_labs", List.of(
                "ParametricLab: client-side interactive lab. User drags sliders; formulas + scene update live (no send_message).",
                "scene: right_triangle | circle | rectangle | linear | quadratic | unit_circle | custom_svg | none.",
                "params: [{id,label,value,min,max,step,unit}]. outputs: [{id,label,expr,format}] with expr like sqrt(a*a+b*b).",
                "Expr allows + - * / ^ ( ) and sqrt,abs,sin,cos,tan,min,max,pow,log,ln,exp,hypot,pi,e.",
                "PythagorasLab: shortcut for 勾股定理 — defaults a,b sliders + c=sqrt(a²+b²) + live triangle.",
                "Example tree root: {kind:'PythagorasLab', props:{title:'勾股定理'}}.",
                "custom_svg: set scene:'custom_svg' and svg:'<svg>...</svg>' with {{a}} placeholders for live numbers."
        ));
        guide.put("concept_animation", List.of(
                "ConceptDemo / AnimStepLab / KnowledgeDemo: narrative animation with play/pause/prev/next/scrub (no chat).",
                "scene: flow | stack | tree | formula | compare | sequence. Omit steps to use built-in demo for that scene.",
                "steps: [{title, caption, duration(ms), highlight:[nodeOrEdgeId], badge}]. highlight drives emphasis + packet motion on edges.",
                "flow/sequence: provide nodes+edges. stack: nodes as layers. tree: nodes+edges. formula: formulas string[]. compare: left/right string[].",
                "Example: {kind:'ConceptDemo', props:{title:'HTTP 请求路径', scene:'flow'}}.",
                "Example formula: {kind:'ConceptDemo', props:{scene:'formula', title:'勾股恒等式', formulas:['a²+b²','→','c²'], steps:[{title:'平方和',caption:'…',highlight:['f0']},…]}}.",
                "Use ConceptDemo when the user should WATCH a concept unfold; use ParametricLab when they should DRAG parameters."
        ));
        guide.put("bind_and_practice", List.of(
                "BindScope: params sliders + children props with {{expr}} or $id update live (no send_message).",
                "Example: {kind:'BindScope', props:{params:[{id:'a',value:3,min:1,max:10}]}, children:[{kind:'NumberLine', props:{value:'{{a}}',min:-10,max:10}}]}.",
                "outputs: [{id:'c', expr:'sqrt(a*a+b*b)'}] adds derived ids usable in {{c}}.",
                "Quiz after a demo: {kind:'Quiz', props:{prompt:'c 等于？', options:['3','4','5'], answer:'c'}} (option id defaults a/b/c).",
                "WorkedExample: reveal steps then final answer. BeforeAfter: drag compare two images/texts.",
                "NumberLine / CoordinateGrid: standalone or inside BindScope. ParametricLab scene=number_line|coordinate also works."
        ));
        guide.put("layout_structure", List.of(
                "Use domain components such as Card, FeatureGrid, KpiBoard, Table and Chart to structure visual output.",
                "Keep nesting shallow (≤3–4 levels) and prefer a small number of meaningful components.",
                "Use the normal assistant message for prose, headings, Markdown and feedback text."
        ));
        guide.put("anti_patterns", List.of(
                "Invalid kind / snake_case kinds.",
                "Props beside kind instead of under props.",
                "Emoji on every line.",
                "Flat 10+ peer nodes without a domain container such as Card."
        ));
        guide.put("subset_note",
                "Reactor currently validates a high-frequency kind subset only. "
                        + "Call list_ui_components; unsupported kinds will fail validation.");
        return guide;
    }
}
