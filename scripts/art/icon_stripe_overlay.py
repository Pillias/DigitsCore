from PIL import Image, ImageDraw
import numpy as np, math, sys
d='/mnt/user-data/uploads/mysterious-brahmagupta/app/src/main/res/drawable/'
V={'ic_launcher_foreground':(217.1,217.9,85.5,108),'ic_launcher_foreground_green':(217.1,219.2,85.3,138),
   'ic_launcher_foreground_orange':(215.9,217.2,85.6,22),'ic_launcher_foreground_red':(215.0,218.4,85.4,-30)}
S=4  # 슈퍼샘플링
def make(n, stripe=True, spots=True):
    x0,y0,R,hend=V[n]
    base=Image.open(d+n+'.png').convert('RGBA'); arr=np.array(base).astype(float)
    W,H=base.size
    ov=Image.new('RGBA',(W*S,H*S),(0,0,0,0)); dr=ImageDraw.Draw(ov)
    def col(deg,r,f):  # 그 자리 몸 색을 밝게(f>0)/어둡게(f<0)
        t=math.radians(deg); c=arr[int(y0+r*math.sin(t)),int(x0+r*math.cos(t))][:3]
        c=c+(255-c)*f if f>0 else c*(1+f)
        return tuple(int(v) for v in c)
    if stripe:
        a0,a1=-48,hend
        steps=int((a1-a0)/1.0)
        for i in range(steps):
            a=a0+i; t=math.radians(a); r=R-6.5
            w=4.2*(0.55+0.45*math.sin(math.pi*i/steps))
            x=(x0+r*math.cos(t))*S; y=(y0+r*math.sin(t))*S
            c=col(a,R-6.5,0.5)
            dr.ellipse([x-w*S/2,y-w*S/2,x+w*S/2,y+w*S/2],fill=c+(200,))
    if spots:
        k=0; a=hend-10
        while a> -45:
            side=2.0 if k%2==0 else -2.5
            t=math.radians(a); r=R+side
            x=(x0+r*math.cos(t))*S; y=(y0+r*math.sin(t))*S; rr=(3.6 if k<3 else 3.0)*S
            dr.ellipse([x-rr,y-rr,x+rr,y+rr],fill=col(a,r,-0.32)+(235,))
            a-=27; k+=1
    ov=ov.resize((W,H),Image.LANCZOS)
    # 몸 바깥으로 넘치지 않게 원본 알파로 자름
    m=np.array(ov); m[...,3]=np.minimum(m[...,3], (arr[...,3]>250)*255); ov=Image.fromarray(m)
    out=base.copy(); out.alpha_composite(ov); return out
if __name__=="__main__":
    names=list(V)
    row=Image.new('RGBA',(300*4,600),(255,255,255,255))
    for i,n in enumerate(names):
        a=Image.open(d+n+'.png').convert('RGBA').crop((66,66,366,366)); b=make(n).crop((66,66,366,366))
        row.alpha_composite(a,(i*300,0)); row.alpha_composite(b,(i*300,300))
    row.convert('RGB').save('ov.png')
    import os; os.makedirs('ic',exist_ok=True)
    for n in names: make(n).save(f'ic/{n}.png')
