from PIL import Image, ImageDraw, ImageFilter
import numpy as np, math
d='store_assets/_drafts/icon_backup_v0940/'   # 원본 아이콘(등줄기 없음)
V={'stable':('ic_launcher_foreground_green',217.1,219.2,190),'fair':('ic_launcher_foreground',217.1,217.9,150),
   'caution':('ic_launcher_foreground_orange',215.9,217.2,76),'rest':('ic_launcher_foreground_red',215.0,218.4,24)}
R=85.5; HB=14.5
BACK=38; FEATHER=10
def sprite(tier):
    n,cx,cy,m=V[tier]
    im=np.array(Image.open(d+n+'.png').convert('RGBA')).astype(float)
    H,W=im.shape[:2]; yy,xx=np.mgrid[0:H,0:W]
    ang=np.degrees(np.arctan2(yy-cy,xx-cx)); rel=(ang-m+540)%360-180
    rad=np.hypot(xx-cx,yy-cy)
    w=np.clip((rel+BACK)/FEATHER,0,1)                 # 뒤쪽(목) 경계를 부드럽게
    w[rel>40]=0
    outside=(np.abs(rad-R)>HB+3.5)&(im[...,3]>200)&(rel>-BACK-28)&(rel<=-BACK+FEATHER)
    w[outside]=np.maximum(w[outside],1.0)
    r_,g_,b_=im[...,0],im[...,1],im[...,2]
    redring=(r_>g_+70)&(rel>8)&(np.abs(rad-R)<HB+3)     # 입 앞의 빨간 링은 빼고 캔버스가 그림
    w[redring]=0
    w[(rad<30)|(rad>150)]=0
    # 링 안쪽 막대(stem) 영역 제외
    stem=(np.abs(xx-cx)<20)&(yy<cy-30)
    w[stem]=0
    out=im.copy(); out[...,3]=im[...,3]*w
    # 몸 색 샘플 (목 뒤 BACK+5도)
    a=math.radians(m-BACK-6)
    samp=[tuple(int(v) for v in im[int(cy+(R+o)*math.sin(a)),int(cx+(R+o)*math.cos(a))][:3]) for o in (-10,-4,2,8,12)]
    stemc=tuple(int(v) for v in im[int(cy-75),int(cx)][:3])
    return Image.fromarray(out.astype(np.uint8)),(cx,cy,m),samp,stemc
SP={t:sprite(t) for t in V}
def tier_of(s): return 'stable' if s>=80 else 'fair' if s>=60 else 'caution' if s>=40 else 'rest'
def gauge(score,S=4):
    t=tier_of(score); spr,(cx,cy,m),samp,stemc=SP[t]
    target=-60+300*score/100
    C=(216,218)
    img=Image.new('RGBA',(432*S,432*S),(0,0,0,0)); dr=ImageDraw.Draw(img)
    def arc(a0,a1,col,w,r=R):
        if a1<=a0: return
        rr_=r+w/2; box=[(C[0]-rr_)*S,(C[1]-rr_)*S,(C[0]+rr_)*S,(C[1]+rr_)*S]; dr.arc(box,a0,a1,fill=col,width=int(w*S))
        for a in (a0,a1):
            tt=math.radians(a); x=(C[0]+r*math.cos(tt))*S; y=(C[1]+r*math.sin(tt))*S; rr=w*S/2; dr.ellipse([x-rr,y-rr,x+rr,y+rr],fill=col)
    arc(-60,240,(234,78,62),2*HB)
    end=target-BACK+4
    # 몸: 바깥→안쪽 색 띠 (아이콘 몸 단면 흉내)
    arc(-60,end,samp[4],2*HB)            # 바깥 그림자
    arc(-60,end,samp[2],2*HB-6,R-2)
    arc(-60,end,samp[1],2*HB-14,R-4)
    img=img.resize((432,432),Image.LANCZOS)
    rot=spr.rotate(-(target-m),resample=Image.BICUBIC,center=(cx,cy),translate=(C[0]-cx,C[1]-cy))
    img.alpha_composite(rot)
    st=Image.new('RGBA',(432*S,432*S),(0,0,0,0)); d2=ImageDraw.Draw(st)
    d2.rounded_rectangle([(C[0]-13)*S,114*S,(C[0]+13)*S,178*S],radius=13*S,fill=stemc+(255,))
    img.alpha_composite(st.resize((432,432),Image.LANCZOS))
    return img
if __name__=="__main__":
    scores=[95,88,80,72,65,55,45,35,25,12]
    out=Image.new('RGBA',(len(scores)*200,420),(255,255,255,255))
    for i,s in enumerate(scores):
        g=gauge(s).crop((76,76,356,356))
        out.alpha_composite(g.resize((190,190),Image.LANCZOS),(i*200,5))
        out.alpha_composite(g.resize((60,60),Image.LANCZOS),(i*200+65,220))
        bg=Image.new('RGBA',(80,80),(29,36,32,255)); bg.alpha_composite(g.resize((60,60),Image.LANCZOS),(10,10)); out.alpha_composite(bg,(i*200+55,300))
    out.convert('RGB').save('hs.png')

# 앱용 머리 그림 저장: 가장 큰 덩어리만 남기고 잘라 냄 (res/drawable-nodpi/gauge_head_*.png)
def export(out_dir):
    from scipy import ndimage
    for t in ['stable','fair','caution','rest']:
        spr,(cx,cy,m),samp,stemc=SP[t]
        a=np.array(spr); lab,n=ndimage.label(a[...,3]>25)
        sizes=ndimage.sum(np.ones_like(lab),lab,range(1,n+1)); keep=1+int(np.argmax(sizes))
        a[...,3]=(a[...,3]*ndimage.binary_dilation(lab==keep,iterations=2)).astype(np.uint8)
        im=Image.fromarray(a); bb=im.getbbox(); im.crop(bb).save(f'{out_dir}/gauge_head_{t}.png')
        print(t, 'left/top', bb[0], bb[1], 'ring', cx, cy, 'mouth', m)
