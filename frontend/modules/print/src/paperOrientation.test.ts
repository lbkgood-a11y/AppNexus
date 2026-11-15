import {expect,it} from 'vitest';
import {orientPaper} from './paperOrientation';
it('preserves dimensions and makes orientation changes idempotent',()=>{
 expect(orientPaper(210,297,true)).toEqual([297,210]);
 expect(orientPaper(297,210,true)).toEqual([297,210]);
 expect(orientPaper(297,210,false)).toEqual([210,297]);
 expect(orientPaper(100,100,true)).toEqual([100,100]);
});
