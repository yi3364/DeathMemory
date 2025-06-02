# DeathMemory

Minecraft 死亡记忆插件，玩家死亡后生成头颅并收集掉落物，支持国际化、功能开关、GUI交互、持久化等。

## 功能概览
- 死亡后生成玩家头颅，头颅可自定义命名（支持时间、ID等）
- 头颅右键打开箱子界面，展示掉落物
- 一键提取、自动装备
- 广播死亡坐标、国际化支持（中英）
- 管理员命令查找所有死亡记录
- 所有功能均可配置开关，数据持久化

## 安装与使用
1. 将插件 jar 放入服务器 plugins 目录，重启服务器。
2. 配置文件详见 `config.yml`，支持功能开关与多语言。
3. 管理员可用命令：`/deathmemory records` 查看所有死亡记录。

## 构建与开发
- 依赖：JDK 17+、Maven、Spigot/Paper 1.16+（推荐 1.21.4）。
- 构建命令：

```powershell
mvn clean package
```

- 生成的 jar 位于 `target/DeathMemory-1.0.0.jar`。

## 自动构建（GitHub Actions 示例）
在 `.github/workflows/maven.yml` 添加：

```yaml
name: Build DeathMemory
on:
  push:
    branches: [ main ]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build with Maven
        run: mvn -B package --file pom.xml
      - name: Upload Artifact
        uses: actions/upload-artifact@v3
        with:
          name: DeathMemory
          path: target/DeathMemory-1.0.0.jar
```

## 贡献与许可
- 欢迎 PR 和 issue。
- 代码遵循 MIT License。
