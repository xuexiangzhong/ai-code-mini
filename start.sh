#!/usr/bin/env bash
# Launch AiCode from any directory.
#
# Usage:
#   ./start.sh                              # JavaFX desktop IDE (default)
#   ./start.sh --cli                        # terminal REPL mode
#   AGENT_NAME="My Agent" ./start.sh --cli  # custom name in CLI mode
#
# The agent operates on your current working directory ($PWD),
# not on the directory where this script lives.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/target/ai-code-mini-agent-0.1.0.jar"

if [ ! -f "$JAR" ]; then
    echo "Building AiCode..."
    (cd "$SCRIPT_DIR" && mvn -q package -DskipTests)
fi

if echo "$*" | grep -q -- '--cli'; then
    exec java -Duser.dir="$PWD" -jar "$JAR" "$@"
else
    (cd "$SCRIPT_DIR" && exec mvn -q javafx:run -Djavafx.jvmArgs="-Duser.dir=$PWD")
fi
