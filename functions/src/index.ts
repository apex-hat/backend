import { onRequest } from "firebase-functions/v2/https";

/**
 * README §3 Architecture: Firebase Functions는 REST API 진입점 역할만 하고,
 * 인증 토큰 검증/비즈니스 로직은 Spring Boot(Backend)가 담당한다.
 * 이 함수는 들어온 요청을 그대로 Backend로 전달하는 순수 프록시다.
 */
const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

const HOP_BY_HOP_HEADERS = new Set(["host", "content-length", "connection"]);

export const api = onRequest(async (req, res) => {
  const targetUrl = `${BACKEND_URL}${req.originalUrl}`;

  const headers: Record<string, string> = {};
  for (const [key, value] of Object.entries(req.headers)) {
    if (!value || HOP_BY_HOP_HEADERS.has(key.toLowerCase())) continue;
    headers[key] = Array.isArray(value) ? value.join(", ") : value;
  }

  const hasBody = !["GET", "HEAD"].includes(req.method);

  const upstream = await fetch(targetUrl, {
    method: req.method,
    headers,
    body: hasBody ? JSON.stringify(req.body) : undefined,
  });

  res.status(upstream.status);
  upstream.headers.forEach((value, key) => res.setHeader(key, value));
  res.send(Buffer.from(await upstream.arrayBuffer()));
});
