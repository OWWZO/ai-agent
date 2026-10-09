package org.wwz.ai.domain.agent.runtime.tool.skill;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class SkillInstallPolicyTest {

    private static SkillThreatScanner.Report report(SkillThreatScanner.Severity severity) {
        return new SkillThreatScanner.Report(List.of(
                new SkillThreatScanner.Finding("test-rule", severity, "SKILL.md", 1, "excerpt")));
    }

    @Test
    public void shouldAllowCleanReports() {
        for (SkillTrustLevel level : SkillTrustLevel.values()) {
            Assert.assertEquals(level.name(),
                    SkillInstallPolicy.Decision.ALLOW,
                    SkillInstallPolicy.decide(level, SkillThreatScanner.Report.empty()));
        }
    }

    @Test
    public void shouldAlwaysAllowBuiltinAndLocal() {
        Assert.assertEquals(SkillInstallPolicy.Decision.ALLOW,
                SkillInstallPolicy.decide(SkillTrustLevel.BUILTIN, report(SkillThreatScanner.Severity.BLOCK)));
        Assert.assertEquals(SkillInstallPolicy.Decision.ALLOW,
                SkillInstallPolicy.decide(SkillTrustLevel.LOCAL, report(SkillThreatScanner.Severity.BLOCK)));
    }

    @Test
    public void shouldOnlyWarnForUploadedSkills() {
        Assert.assertEquals(SkillInstallPolicy.Decision.WARN,
                SkillInstallPolicy.decide(SkillTrustLevel.UPLOAD, report(SkillThreatScanner.Severity.BLOCK)));
    }

    @Test
    public void shouldBlockUrlSkillsOnBlockSeverity() {
        Assert.assertEquals(SkillInstallPolicy.Decision.BLOCK,
                SkillInstallPolicy.decide(SkillTrustLevel.URL, report(SkillThreatScanner.Severity.BLOCK)));
    }

    @Test
    public void shouldOnlyWarnForUrlSkillsWithWarnSeverity() {
        Assert.assertEquals(SkillInstallPolicy.Decision.WARN,
                SkillInstallPolicy.decide(SkillTrustLevel.URL, report(SkillThreatScanner.Severity.WARN)));
    }

    @Test
    public void shouldDefaultToUploadWhenLevelIsNull() {
        Assert.assertEquals(SkillInstallPolicy.Decision.WARN,
                SkillInstallPolicy.decide(null, report(SkillThreatScanner.Severity.BLOCK)));
    }

    @Test
    public void shouldMapSourceToTrustLevel() {
        Assert.assertEquals(SkillTrustLevel.URL, SkillTrustLevel.fromSource("url"));
        Assert.assertEquals(SkillTrustLevel.UPLOAD, SkillTrustLevel.fromSource("UPLOAD"));
        Assert.assertEquals(SkillTrustLevel.LOCAL, SkillTrustLevel.fromSource("local"));
        Assert.assertEquals(SkillTrustLevel.BUILTIN, SkillTrustLevel.fromSource(null));
        Assert.assertEquals(SkillTrustLevel.BUILTIN, SkillTrustLevel.fromSource("unknown"));
    }
}
