/** 从调用方已获授权的选项解析引用名；未设置显示占位，找不到名称时显示本地化提示。 */
export function referenceName<T extends { id: string }>(
  id: string | null | undefined,
  options: readonly T[],
  label: (option: T) => string,
  unavailable: string
): string {
  if (!id) return "—";
  const option = options.find(item => item.id === id);
  return (option && label(option).trim()) || unavailable;
}
