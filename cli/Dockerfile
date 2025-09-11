FROM debian:bookworm-slim
WORKDIR /app

# ARG set by buildx depending on target platform
ARG TARGETARCH
ARG AMD64_BINARY_DIR
ARG ARM64_BINARY_DIR

# Copy the right binary
RUN --mount=type=bind,source=$AMD64_BINARY,target=/tmp/amd64,readonly \
    --mount=type=bind,source=$ARM64_BINARY,target=/tmp/arm64,readonly \
    if [ "$TARGETARCH" = "amd64" ]; then cp /tmp/amd64/divyam /usr/local/bin/divyam; else cp /tmp/arm64/divyam /usr/local/bin/divyam; fi

ENTRYPOINT ["divyam"]
