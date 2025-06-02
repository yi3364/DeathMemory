# DeathMemory 插件维护与发布指南

## 日常开发/维护流程

1. **拉取最新代码**
   ```powershell
   git pull origin main
   ```

2. **本地开发与调试**
   - 修改或新增 Java/资源文件，推荐使用 IDEA/VSCode 等 IDE。
   - 本地编译测试：
     ```powershell
     mvn clean package
     ```
   - 生成的 jar 在 `target/DeathMemory-*.jar`，可直接放入服务器 plugins 目录测试。

3. **提交与推送**
   ```powershell
   git add .
   git commit -m "feat: xxx/修复: xxx"
   git push origin main
   ```

4. **自动构建检查**
   - 每次 push 后，GitHub Actions 会自动编译并检查构建。
   - 可在 GitHub 仓库的 Actions 页面查看构建状态。

## 发布新版本（自动生成 Release 附件与变更日志）

1. **打 tag 并推送**
   ```powershell
   git tag v1.1.0
   git push origin v1.1.0
   ```
2. **自动发布 Release**
   - GitHub Actions 会自动构建 jar 并发布 Release，附带详细说明和自动生成的变更日志。
   - Release 页面可下载 jar 包。

## 变更日志与说明
- 变更日志自动根据 PR/commit 分类生成，无需手动维护。
- 如需自定义 changelog 分类，可编辑 `.github/release-changelog-config.json`。

## 常见问题
- **依赖问题**：如遇依赖无法下载，优先检查 pom.xml 和网络。
- **兼容性**：如升级 Spigot/Paper 版本，建议同步升级 spigot-api 依赖。
- **国际化**：如需新增语言，直接添加 `messages_xx_XX.properties` 文件。

## 贡献/协作建议
- 统一使用 PR 或 issue 跟踪需求和 bug。
- 代码风格、注释、国际化、配置开关等请参考现有实现。

---
如有疑问可在 GitHub issue 区留言，或联系维护者。
