import sys, math, re, xml.etree.ElementTree as ET
sys.path.insert(0,'.')
from gen import build
def col(c, op=None):
    c=c.lstrip('#'); a='FF' if op is None else '%02X'%round(float(op)*255)
    return '#'+a+c.upper()
def ellipse_path(cx,cy,rx,ry,rot=0):
    # 회전된 타원: 두 개의 호
    t=math.radians(rot)
    p1=(cx+rx*math.cos(t), cy+rx*math.sin(t)); p2=(cx-rx*math.cos(t), cy-rx*math.sin(t))
    return f"M{p1[0]:.2f},{p1[1]:.2f}A{rx:.2f},{ry:.2f} {rot:.1f} 1,1 {p2[0]:.2f},{p2[1]:.2f}A{rx:.2f},{ry:.2f} {rot:.1f} 1,1 {p1[0]:.2f},{p1[1]:.2f}Z"
def convert(svg):
    root=ET.fromstring(svg)
    vb=[float(x) for x in root.get('viewBox').split()]
    out=[]
    for el in root:
        tag=el.tag.split('}')[-1]; g=el.get
        fill=g('fill'); stroke=g('stroke'); op=g('opacity')
        if tag=='circle':
            d=ellipse_path(float(g('cx')),float(g('cy')),float(g('r')),float(g('r')))
        elif tag=='ellipse':
            rot=0; tr=g('transform') or ''
            m=re.match(r'rotate\(([-\d.]+)',tr); rot=float(m[1]) if m else 0
            d=ellipse_path(float(g('cx')),float(g('cy')),float(g('rx')),float(g('ry')),rot)
        elif tag=='line':
            d=f"M{g('x1')},{g('y1')}L{g('x2')},{g('y2')}"
        elif tag=='path':
            d=g('d').replace(' L',' L')
        else: continue
        attrs=[f'android:pathData="{d}"']
        if fill and fill!='none': attrs.append(f'android:fillColor="{col(fill,op)}"')
        if stroke and stroke!='none':
            attrs.append(f'android:strokeColor="{col(stroke,op or g("stroke-opacity"))}"'); attrs.append(f'android:strokeWidth="{g("stroke-width")}"')
            if g('stroke-linecap')=='round': attrs.append('android:strokeLineCap="round"')
            attrs.append('android:strokeLineJoin="round"')
        out.append('        <path '+' '.join(attrs)+'/>')
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- 도룡뇽 성장 단계 그림 (store_assets/coryong/v3 에서 생성). 손으로 고치지 말고 생성 스크립트로 다시 만듭니다. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp" android:height="48dp"
    android:viewportWidth="{vb[2]:.1f}" android:viewportHeight="{vb[3]:.1f}">
    <group android:translateX="{-vb[0]:.1f}" android:translateY="{-vb[1]:.1f}">
'''+"\n".join(out)+'''
    </group>
</vector>
'''
import os; os.makedirs('vd',exist_ok=True)
for s in range(1,5):
    open(f'vd/ic_salamander_stage{s}.xml','w').write(convert(build(s)))
    open(f'vd/stage{s}.svg','w').write(build(s))
print('ok')
