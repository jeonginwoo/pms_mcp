/*
 * 어시스턴트 답변 한 개 — 마크다운으로 그린다.
 *
 * 프롬프트가 표를 허용하고 있어서(`SystemPrompts` "[톤과 형식] … 표가 더 명확할 때만
 * 간단한 표") 굵게·목록에 더해 GFM 표가 실제로 온다. remark-gfm이 그 표를 맡는다.
 *
 * **원칙 6이 여기서 실무가 된다** — 답변에는 프로젝트 설명·이력 비고 같은 DB 텍스트가
 * 그대로 섞여 들어온다. 그래서 그 텍스트가 만들 수 있는 것을 좁힌다:
 *  - `skipHtml` — 원시 HTML을 통째로 버린다(rehype-raw를 안 붙였으므로 실행될 일은
 *    애초에 없지만, 글자로 새어 나오는 것도 막는다). `dangerouslySetInnerHTML`은
 *    이 앱에서 금지다(react-ts 규약 §4) — react-markdown은 그것을 쓰지 않는다
 *  - `img` 금지 — **클릭 없이 외부 요청을 내는 유일한 태그**다. 남의 DB 문자열이 우리
 *    화면에서 추적 픽셀이 되는 경로를 닫는다
 *  - 링크는 새 탭 + `noreferrer` — URL도 결국 데이터고, 우리 화면 문맥을 넘겨주지 않는다
 *    (`javascript:` 류는 react-markdown 기본 urlTransform이 이미 막는다)
 */
import Markdown from 'react-markdown'
import type { Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'

const BLOCKED_TAGS = ['img']

const COMPONENTS: Components = {
  a: ({ href, children }) => (
    <a href={href} target="_blank" rel="noreferrer noopener">{children}</a>
  ),
}

export default function ChatAnswer({ text }: { text: string }) {
  return (
    <div className="bubble ai md">
      <Markdown
        remarkPlugins={[remarkGfm]}
        skipHtml
        disallowedElements={BLOCKED_TAGS}
        components={COMPONENTS}
      >
        {text}
      </Markdown>
    </div>
  )
}
