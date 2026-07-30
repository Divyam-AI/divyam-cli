FROM debian:bookworm-slim
WORKDIR /app

# ARG set by buildx depending on target platform
ARG TARGETARCH
ARG AMD64_BINARY_DIR
ARG ARM64_BINARY_DIR

RUN apt-get update \
    && apt-get install --no-install-recommends -y bash bash-completion \
    && rm -rf /var/lib/apt/lists/*

COPY docker-bashrc /root/.bashrc

RUN --mount=type=bind,source=$AMD64_BINARY_DIR,target=/tmp/amd64,readonly \
    --mount=type=bind,source=$ARM64_BINARY_DIR,target=/tmp/arm64,readonly \
    if [ "$TARGETARCH" = "amd64" ]; then \
      cp /tmp/amd64/divyam /usr/local/bin/divyam; \
      cp /tmp/amd64/divyam_completion /usr/share/bash-completion/completions/divyam; \
    else \
      cp /tmp/arm64/divyam /usr/local/bin/divyam; \
      cp /tmp/arm64/divyam_completion /usr/share/bash-completion/completions/divyam; \
    fi

RUN chmod +x /usr/local/bin/divyam /usr/share/bash-completion/completions/divyam

ENTRYPOINT ["/usr/local/bin/divyam"]
