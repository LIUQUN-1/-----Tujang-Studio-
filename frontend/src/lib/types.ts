/** 与 docs/api.md 对齐的接口类型。字段变更先改契约再改这里。 */

export type Category = {
  code: string;
  name: string;
  /** 该分类下的模板数 */
  count: number;
};

export type TemplateSummary = {
  id: string;
  title: string;
  categoryCode: string;
  categoryName: string;
  coverUrl: string;
  description: string;
  /** 该模板需要几张素材 */
  requiredImageCount: number;
};

export type TemplateDetail = TemplateSummary & {
  acceptedMimeTypes: string[];
  maxFileSizeBytes: number;
};

/** 非 2xx 响应体（docs/api.md §0.5） */
export type ApiErrorBody = {
  code: string;
  message: string;
  fields?: Record<string, string>;
};
