# React / TypeScript Conventions (frontend/)

> Scope: the PMS web client (`frontend/`, pms track) and — once it exists — the
> chat widget. Referenced from `pms/CLAUDE.md`.
>
> `frontend/` was rewritten in TypeScript on 2026-08-22 (real backend wiring, the
> old prototype's design only): there are **no `.js`/`.jsx` files left**, so the
> former "new files in TS, migrate the rest incrementally" clause is retired.
> The backend-free mock app is `prototype/` and these conventions do not govern it.

## 1. Components
- **Function components only.** Class components are forbidden.
- One file = one component. File names are PascalCase (`ChatPanel.tsx`).
- Declare props as `interface Props`, and export as `export default function ChatPanel({ ... }: Props)`.
- Consider splitting any component that exceeds 150 lines.

## 2. Types
- `any` is forbidden. When unavoidable, use `unknown` + narrowing.
- Collect API response types in `src/types/` (currently `src/types/api.ts`) and match their names to the server DTOs (Ubiquitous Language).
- Minimize type assertions (`as`) — prefer type guard functions.

## 3. State & data
- Local state via `useState`/`useReducer`; server state goes through the fetch wrapper (`src/api.ts`) — no direct `fetch` calls inside components.
- **The response envelope is unwrapped in exactly one place.** Every PRD-pms §7 response is `{success, data}` or `{success, error}`, and `api.ts`'s `unwrap` is the only code that knows it — a 2xx body that is not an envelope fails there as `MALFORMED_RESPONSE` rather than flowing on as `undefined`. Components see plain domain values.
- Global state follows the existing store pattern (`src/store.tsx` — a `StoreContext` + `StoreProvider` over `useState`, consumed via the `useStore()` hook that throws outside the provider). Introducing a state library follows the `기술_선택_근거` process.
- Never omit the `useEffect` dependency array. Do not suppress lint warnings.
- **Only call endpoints that exist.** This app is wired to the real `pms/` API, and unimplemented use cases answer `501 NOT_IMPLEMENTED` (PRD-pms §10) — a route that is scaffolded on the server is not a route the client may render as working.

## 4. Chat-widget-specific rules (PRD-host FR-AI series — the widget shipped 2026-08-27 as `ChatPanel.tsx`; numbers re-confirmed against PRD-host §6 that day)

> The panel talks to the canonical path `POST /api/chat`, but the pms chat BFF does not
> exist yet — a vite proxy entry aims that one path at the host stand-in (8081). Two of
> the five rules below are blocked on that (shared decision log, 2026-08-27).

- Over 2,000 input characters: disable the send button + show guidance — **block before it reaches the server** (FR-AI-02). **Met** — `MAX_INPUT` in `ChatPanel.tsx`. The BFF will cut at the same number server-side; that does not replace this rule.
- Show progress state while waiting for a response + provide an abort button (FR-AI-03). **Met** — the send button becomes 중단 while pending. Two limits are stated in code, not hidden: the per-tool wording the PRD shows ("가동률 조회 중...") needs a streaming response and the stand-in is synchronous; and aborting stops the *wait*, not the server — a write already confirmed stays committed.
- Write operations must render a confirmation card ([실행]/[취소]) — never create a write path without the card (FR-AI-04). **Not met — blocked on the response contract.** A card needs the response to say "a write is pending, here is the summary"; the stand-in returns `{conversationId, reply}`, i.e. text only. What guards writes meanwhile is principle 5's 2-step confirmation, which happens in the conversation and is judged by the agent and the server. Settle the signal when the BFF contract is fixed (M1).
- Every response gets 👍/👎 feedback buttons; 👎 asks for a reason (FR-AI-05). **Not met — `POST /api/chat/feedback` does not exist.** §3's last rule forbids rendering an absent route as working, so the buttons wait for the BFF too.
- Render tool results and model responses **as a bounded element tree** — `dangerouslySetInnerHTML` is forbidden (injection defense). **Amended 2026-08-27** from "as text": the prompt permits tables (`SystemPrompts` "[톤과 형식]") and GFM tables do arrive, so answers render through react-markdown. The defense is kept by narrowing what DB text can produce (`ChatAnswer.tsx`): no `dangerouslySetInnerHTML` (react-markdown builds elements), `skipHtml` drops raw HTML, and **`img` is disallowed** — it is the one tag that issues an outbound request with no click. User input and our own error strings are never parsed as markdown.

## 5. Style & quality
- Follow the existing `styles.css`/`theme.ts` system. Inline styles only for dynamic values.
- Keep ESLint warnings at 0. `eslint-disable` requires a reason comment.
- **Tests**: `npm test` is currently `tsc --noEmit` — there is **no Vitest setup yet**, because the app holds no non-trivial pure logic and `verify.sh` needs a green stage today. Add Vitest at the first of these triggers, and update this line when you do: a parser / date or period calculation / permission derivation worth testing in isolation (`periods.ts`, `permissions.ts` are the likely first), or the chat widget's confirmation-card flow (FR-AI-04), which requires a component test.
