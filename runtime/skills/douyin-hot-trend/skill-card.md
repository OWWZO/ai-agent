## Description: <br>
获取抖音热榜/热搜榜数据，包含热门视频、挑战赛、音乐等多领域热门内容，并输出标题、热度值、跳转链接及封面图（如有）。 <br>

This skill is ready for commercial/non-commercial use. <br>

## Publisher: <br>
[franklu0819-lang](https://clawhub.ai/user/franklu0819-lang) <br>

### License/Terms of Use: <br>
MIT-0 <br>


## Use Case: <br>
Developers, content analysts, marketers, and social media operators use this skill to fetch current Douyin hot-list items for trend tracking, content planning, and social media operations. <br>

### Deployment Geography for Use: <br>
Global <br>

## Known Risks and Mitigations: <br>
Risk: The bundled helper scripts include under-disclosed Telegram-style routing through a hard-coded chat ID. <br>
Mitigation: Remove or edit cron-job.js before scheduled use and replace the hard-coded chat ID with user-controlled configuration. <br>
Risk: A helper script uses a shell-command pattern that is unsafe when the limit argument is not strictly numeric and trusted. <br>
Mitigation: Use the documented node scripts/douyin.js hot [number] command with a numeric limit, and avoid scripts/get-hot-trend.js with untrusted input. <br>
Risk: The Douyin web endpoint may change structure or trigger platform controls under frequent access. <br>
Mitigation: Review outputs before relying on them and use reasonable request frequency. <br>


## Reference(s): <br>
- [ClawHub skill page](https://clawhub.ai/franklu0819-lang/douyin-hot-trend) <br>
- [ClawHub publisher profile](https://clawhub.ai/user/franklu0819-lang) <br>
- [Douyin web source](https://www.douyin.com/) <br>


## Skill Output: <br>
**Output Type(s):** [Text, Markdown, JSON, Shell commands] <br>
**Output Format:** [Console text, Markdown messages, and JSON files containing ranked Douyin trend data.] <br>
**Output Parameters:** [1D] <br>
**Other Properties Related to Output:** [Outputs include rank, title, popularity, link, optional cover URL, optional label, and content type when available.] <br>

## Skill Version(s): <br>
1.1.0 (source: frontmatter, package.json, release evidence) <br>

## Ethical Considerations: <br>
Users should evaluate whether this skill is appropriate for their environment, review any generated or modified files before relying on them, and apply their organization's safety, security, and compliance requirements before deployment. <br>
