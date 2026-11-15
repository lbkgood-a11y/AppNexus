/**
 * 按方向整理纸张宽高，仅交换已有尺寸，不缩放纸张内容。
 * @param width 纸张宽度，单位 mm。
 * @param height 纸张高度，单位 mm。
 * @param landscape true 为横向，false 为纵向；正方形尺寸保持不变。
 * @returns 按所选方向排列的宽高。
 */
export function orientPaper(width:number,height:number,landscape:boolean):[number,number]{
  return landscape?[Math.max(width,height),Math.min(width,height)]:[Math.min(width,height),Math.max(width,height)];
}
