# Code Quality Checklist

**Every service: static analysis in CI, zero HIGH/CRITICAL CVEs, no secrets in code.**

---

## Static Analysis (CI Gate)

| Language | Tools | CI Config |
|----------|-------|-----------|
| Java/Kotlin | SpotBugs, PMD, Checkstyle, Error Prone | `pom.xml` profiles |
| TypeScript | ESLint + @typescript-eslint, Prettier | `.eslintrc.js`, `prettier.config.js` |
| Go | golangci-lint (all enabled) | `.golangci.yml` |
| Python | Ruff, mypy, bandit | `pyproject.toml` |
| All | Trivy (container), Grype (SBOM) | GitHub Actions |

**Threshold:** Build fails on any warning (configure as error).

---

## Maven Example — `pom.xml` Quality Profile

```xml
<properties>
  <spotbugs.version>4.8.6</spotbugs.version>
  <pmd.version>6.55.0</pmd.version>
  <checkstyle.version>3.3</checkstyle.version>
  <errorprone.version>2.22.1</errorprone.version>
</properties>

<build>
  <plugins>
    <!-- SpotBugs -->
    <plugin>
      <groupId>com.github.spotbugs</groupId>
      <artifactId>spotbugs-maven-plugin</artifactId>
      <version>${spotbugs.version}</version>
      <configuration>
        <effort>Max</effort>
        <threshold>Low</threshold>
        <failOnError>true</failOnError>
        <excludeFilterFile>${project.basedir}/spotbugs-exclude.xml</excludeFilterFile>
      </configuration>
      <executions>
        <execution><phase>verify</phase><goals><goal>check</goal></goals></execution>
      </executions>
    </plugin>

    <!-- PMD -->
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-pmd-plugin</artifactId>
      <version>${pmd.version}</version>
      <configuration>
        <rulesets>
          <ruleset>/rulesets/java/quickstart.xml</ruleset>
          <ruleset>/rulesets/java/design.xml</ruleset>
        </rulesets>
        <failOnViolation>true</failOnViolation>
        <printFailingErrors>true</printFailingErrors>
      </configuration>
      <executions>
        <execution><phase>verify</phase><goals><goal>check</goal></goals></execution>
      </executions>
    </plugin>

    <!-- Checkstyle -->
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-checkstyle-plugin</artifactId>
      <version>${checkstyle.version}</version>
      <configuration>
        <configLocation>checkstyle.xml</configLocation>
        <failOnViolation>true</failOnViolation>
      </configuration>
      <executions>
        <execution><phase>verify</phase><goals><goal>check</goal></goals></execution>
      </executions>
    </plugin>

    <!-- Error Prone -->
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <version>3.13.0</version>
      <configuration>
        <compilerArgs>
          <arg>-Xlint:all</arg>
          <arg>-Werror</arg>
        </compilerArgs>
        <annotationProcessorPaths>
          <path>
            <groupId>com.google.errorprone</groupId>
            <artifactId>error_prone_core</artifactId>
            <version>${errorprone.version}</version>
          </path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>

    <!-- Enforcer: Java version, banned deps -->
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-enforcer-plugin</artifactId>
      <version>3.5.0</version>
      <executions>
        <execution>
          <id>enforce-java</id>
          <phase>validate</phase>
          <goals><goal>enforce</goal></goals>
          <configuration>
            <rules>
              <requireJavaVersion>
                <version>21</version>
              </requireJavaVersion>
              <bannedDependencies>
                <searchTransitive>true</searchTransitive>
                <excludes>
                  <exclude>org.slf4j:slf4j-log4j12</exclude>
                  <exclude>log4j:log4j</exclude>
                </excludes>
              </bannedDependencies>
            </rules>
          </configuration>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

---

## Dependency Management

| Rule | Enforcement |
|------|-------------|
| No `SNAPSHOT` in release | Enforcer `bannedDependencies` |
| No known vulnerable deps | `org.owasp:dependency-check-maven` in CI |
| Auto-update via Dependabot | `.github/dependabot.yml` weekly |
| License compliance | `license-maven-plugin` — no GPL/AGPL in proprietary |

---

## Secrets Prevention

| Layer | Tool |
|-------|------|
| Pre-commit | `git-secrets` / `trufflehog` / `gitleaks` |
| CI | `trufflehog` scan on PR |
| Container | `trivy fs . --security-checks vuln,secret,config` |
| Runtime | Vault / Doppler / GitHub Environments |

**Never commit:** `.env`, `*.pem`, `*.key`, `*.jks`, `id_rsa`, AWS keys, DB passwords.

---

## Observability Code Standards

| Standard | Implementation |
|----------|----------------|
| Correlation ID | `X-Request-Id` header → MDC → all logs |
| Structured JSON logs | Logstash/Logback encoder, no plain text |
| Metrics | Micrometer + Prometheus, low cardinality |
| Traces | OpenTelemetry SDK, W3C traceparent |
| Health | Spring Boot Actuator + custom indicators |

---

## Verification Commands

```bash
# Static analysis
mvn -B verify  # fails on any warning

# Dependency check
mvn org.owasp:dependency-check-maven:check

# Secrets scan
trufflehog git file://. --only-verified
gitleaks detect --source .

# Container scan
trivy image your-image:tag --severity HIGH,CRITICAL
```