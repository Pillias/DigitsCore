from PIL import Image, ImageDraw
import numpy as np, math, json, os
d='/tmp/claude-0/ic/'   # 등줄기 선 들어간 확정 아이콘
CX,CY,R,BW=217.0,218.0,85.5,28.0
V={'stable':('ic_launcher_foreground_green',180),'fair':('ic_launcher_foreground',140),'caution':('ic_launcher_foreground_orange',77),'rest':('ic_launcher_foreground_red',23)}
BACK=46; BITE=9; OVER=40
def ang_field(H,W):
    yy,xx=np.mgrid[0:H,0:W]
    a=np.degrees(np.arctan2(yy-CY,xx-CX)); r=np.hypot(xx-CX,yy-CY); return a,r
def make_sprite(t):
    n,m=V[t]
    im=np.array(Image.open(d+n+'.png').convert('RGBA')).astype(float)
    H,W=im.shape[:2]; a,r=ang_field(H,W)
    rel=(a-m+540)%360-180
    inband=np.abs(r-R)<=BW/2+1.5
    # 뒤쪽 경계: 몸 띠 안에서는 BACK도에서 3도에 걸쳐 부드럽게
    back=np.clip((rel+BACK)/3.0,0,1)
    m_band=back*(rel<=BITE)
    m_out=back*(rel<=OVER)*(r<R+60)*(r>R-60)
    mask=np.where(inband,m_band,m_out)
    sp=im.copy(); sp[...,3]=im[...,3]*mask
    ys,xs=np.nonzero(sp[...,3]>2); x0,x1,y0,y1=xs.min(),xs.max()+1,ys.min(),ys.max()+1
    Image.fromarray(sp[y0:y1,x0:x1].astype(np.uint8)).save(f'spr/salamander_head_{t}.png')
    def samp(ang,rr):
        tt=math.radians(ang); c=im[int(round(CY+rr*math.sin(tt))),int(round(CX+rr*math.cos(tt)))][:3]; return '#%02X%02X%02X'%tuple(int(v) for v in c)
    tail=samp(-50,R); cut=samp(m-BACK-2,R)
    shade={'stable':'#1EA25A','fair':'#1EA159','caution':'#64922D','rest':'#8D5437'}[t]
    stem=[ '#%02X%02X%02X'%tuple(int(v) for v in im[y,217][:3]) for y in (116,176)]
    return dict(x0=int(x0),y0=int(y0),mouth=m,tail=tail,cut=cut,shade=shade,stem=stem)
os.makedirs('spr',exist_ok=True)
META={t:make_sprite(t) for t in V}
json.dump(META,open('spr/meta.json','w'),indent=1)
print(json.dumps(META,indent=1))
def tier_of(s): return 'stable' if s>=80 else 'fair' if s>=60 else 'caution' if s>=40 else 'rest'
def hexc(h): return tuple(int(h[i:i+2],16) for i in (1,3,5))
def lerp(c1,c2,f): return tuple(int(a+(b-a)*f) for a,b in zip(c1,c2))
def gauge(score,S=4):
    t=tier_of(score); M=META[t]
    target=-60+300*max(score,10)/100.0      # 입 위치
    cut=target-BACK
    img=Image.new('RGBA',(432*S,432*S),(0,0,0,0)); dr=ImageDraw.Draw(img)
    def seg(a,col,r=R,w=BW,dx=0,dy=0):
        tt=math.radians(a); x=(CX+dx+r*math.cos(tt))*S; y=(CY+dy+r*math.sin(tt))*S; rr=w*S/2
        dr.ellipse([x-rr,y-rr,x+rr,y+rr],fill=col)
    for i in range(0,301): seg(-60+i,(0xE7,0x48,0x38))            # 빨간 링
    tc,cc=hexc(M['tail']),hexc(M['cut']); sh=hexc(M['shade'])
    if cut>-60:
        steps=int(cut+60)+2
        for i in range(steps): seg(-60+i*(cut+60+1)/steps,sh,dx=2.2,dy=3.2)   # 아래쪽 그림자
        for i in range(steps):
            a=-60+i*(cut+60+1)/steps; seg(a,lerp(tc,cc,i/max(1,steps-1)))
    img=img.resize((432,432),Image.LANCZOS)
    st=Image.open(f'spr/salamander_head_{t}.png')
    layer=Image.new('RGBA',(432,432),(0,0,0,0)); layer.alpha_composite(st,(M['x0'],M['y0']))
    layer=layer.rotate(-(target-M['mouth']),resample=Image.BICUBIC,center=(CX,CY))
    img.alpha_composite(layer)
    # 막대 (그라데이션)
    s1,s2=hexc(M['stem'][0]),hexc(M['stem'][1])
    sl=Image.new('RGBA',(432*S,432*S),(0,0,0,0)); d2=ImageDraw.Draw(sl)
    for y in range(114*S,178*S):
        f=(y-114*S)/(64*S); c=lerp(s1,s2,f)
        half=13*S; 
        # 둥근 끝
        top=114*S+half; bot=178*S-half
        if y<top: w=math.sqrt(max(0,half**2-(top-y)**2))
        elif y>bot: w=math.sqrt(max(0,half**2-(y-bot)**2))
        else: w=half
        d2.line([(217*S-w,y),(217*S+w,y)],fill=c+(255,))
    img.alpha_composite(sl.resize((432,432),Image.LANCZOS))
    return img.crop((217-132,218-132,217+132,218+132))
if __name__=="__main__":
    scores=[95,85,75,65,55,45,35,15]
    out=Image.new('RGBA',(len(scores)*210,320),(255,255,255,255)); dr=ImageDraw.Draw(out)
    for i,s in enumerate(scores):
        g=gauge(s).resize((200,200),Image.LANCZOS); out.alpha_composite(g,(i*210+5,5)); dr.text((i*210+95,208),str(s),fill=(0,0,0))
        sm=gauge(s).resize((63,63),Image.LANCZOS); out.alpha_composite(sm,(i*210+70,235))
    out.convert('RGB').save('spr_gauge.png')
