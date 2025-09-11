FROM debian:bookworm-slim
WORKDIR /app

# ARG set by buildx depending on target platform
ARG TARGETARCH
ARG AMD64_BINARY
ARG ARM64_BINARY

# Copy the right binary
RUN --mount=type=bind,source=$AMD64_BINARY,target=/tmp/amd64,readonly \
    --mount=type=bind,source=$ARM64_BINARY,target=/tmp/arm64,readonly \
    if [ "$TARGETARCH" = "amd64" ]; then cp /tmp/amd64 /usr/local/bin/divyam-cli; else cp /tmp/arm64 /usr/local/bin/divyam-cli; fi

ENTRYPOINT ["divyam-cli"]
