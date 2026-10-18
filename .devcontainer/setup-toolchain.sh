#!/usr/bin/env bash
# 为 Maven toolchain 准备 JDK 17 的声明（~/.m2/toolchains.xml）。
#
# 为什么需要它（2026-10-18 真机踩到 ✨✓）：
#   pom.xml 里 maven-toolchains-plugin 要求 JDK 17 ✓✓，
#   但全新环境里 ~/.m2/toolchains.xml **不存在** ✗✗ →
#   `mvn spring-boot:run` 直接报
#     Cannot find matching toolchain definitions for the following toolchain types: jdk [ version='17' ]
#   而且这个文件还是**隐藏的**（.m2 开头 ✓✗）→ 在 VS Code 里搜不到 ✗✓
#   → 有人会去全局搜 → 把 4GB 的 CodeSpaces 搜崩 ✗✗
#
# 关键设计：**动态探测 JDK 路径**，不写死 ✓✓
#   不同 devcontainer 镜像的 JDK 位置不一样（/usr/lib/jvm/java-17-openjdk-amd64、
#   /usr/lib/jvm/msopenjdk-current、/usr/local/sdkman/... 全都有可能 ✨✓）
#   写死路径 = 换个镜像就废 ✗✗
set -euo pipefail

echo "[setup-toolchain] 探测 JDK 路径..."

# javac 的上级的上级就是 JDK_HOME ✓✓（例：/usr/lib/jvm/java-17/bin/javac → /usr/lib/jvm/java-17）
JDK_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
echo "[setup-toolchain] JDK_HOME = $JDK_HOME"

# 顺带验版本：必须是 17 ✓✓（Spring Boot 3.2.5 只支持 17–21 ✗✓）
VERSION="$("$JDK_HOME/bin/javac" -version 2>&1 | awk '{print $2}')"
echo "[setup-toolchain] javac 版本 = $VERSION"
case "$VERSION" in
  17.*) echo "[setup-toolchain] 版本符合 ✓" ;;
  *) echo "[setup-toolchain] ⚠️ 版本不是 17（当前 $VERSION）—— Spring Boot 3.2.5 官方支持 17–21，建议换镜像 ✗" ;;
esac

mkdir -p "$HOME/.m2"
cat > "$HOME/.m2/toolchains.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<toolchains>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>17</version>
      <vendor>$(basename "$JDK_HOME")</vendor>
    </provides>
    <configuration>
      <jdkHome>$JDK_HOME</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
EOF

echo "[setup-toolchain] 已写入 $HOME/.m2/toolchains.xml ✓"
echo "[setup-toolchain] 完成 —— 现在可以直接：cd server && mvn spring-boot:run"