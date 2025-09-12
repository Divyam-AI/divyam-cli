FROM debian:bookworm-slim
WORKDIR /app

# ARG set by buildx depending on target platform
ARG TARGETARCH
ARG AMD64_BINARY_DIR
ARG ARM64_BINARY_DIR

RUN --mount=type=bind,source=$AMD64_BINARY_DIR,target=/tmp/amd64,readonly \
    --mount=type=bind,source=$ARM64_BINARY_DIR,target=/tmp/arm64,readonly \
    if [ "$TARGETARCH" = "amd64" ]; then \
      cp /tmp/amd64/divyam /usr/local/bin/divyam; \
    else \
      cp /tmp/arm64/divyam /usr/local/bin/divyam; \
    fi

RUN chmod +x /usr/local/bin/divyam

ENTRYPOINT ["/usr/local/bin/divyam"]
