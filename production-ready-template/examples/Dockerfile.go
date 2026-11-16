# ── Build Stage ──────────────────────────────────────────────────────────
FROM golang:1.22-alpine AS build

WORKDIR /workspace

# Cache dependencies
COPY go.mod go.sum ./
RUN go mod download

# Build
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o /app/server .

# ── Runtime Stage ────────────────────────────────────────────────────────
FROM alpine:3.19

# Non-root user
RUN addgroup -g 1001 -S appgroup && adduser -u 1001 -S appuser -G appgroup

WORKDIR /app

# Copy binary
COPY --from=build --chown=appuser:appgroup /app/server .

# Runtime config
ENV PORT=8080
ENV GIN_MODE=release

USER appuser:appgroup

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

ENTRYPOINT ["./server"]