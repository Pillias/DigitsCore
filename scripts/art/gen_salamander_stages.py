import math, json
def bez(p, t):
    (x0,y0),(x1,y1),(x2,y2),(x3,y3)=p
    u=1-t
    return (u**3*x0+3*u*u*t*x1+3*u*t*t*x2+t**3*x3, u**3*y0+3*u*u*t*y1+3*u*t*t*y2+t**3*y3)
def spine_pts(ctrl, n=120):
    pts=[bez(ctrl,i/n) for i in range(n+1)]
    return pts
def normals(pts):
    out=[]
    for i,p in enumerate(pts):
        a=pts[max(i-1,0)]; b=pts[min(i+1,len(pts)-1)]
        dx,dy=b[0]-a[0],b[1]-a[1]; L=math.hypot(dx,dy) or 1
        out.append((-dy/L,dx/L,dx/L,dy/L))
    return out
def fmt(pts): return "M"+" L".join(f"{x:.1f},{y:.1f}" for x,y in pts)+" Z"
STAGES={
 # ctrl: spine from head tip (t=0) to tail tip (t=1)
 1:dict(ctrl=[(150,70),(115,85),(95,130),(60,150)], head=0.30, w=lambda t: profile(t,25,16,21,0.30,0.64), legs=[], gills=1.0, fin=True, spots=0),
 2:dict(ctrl=[(158,62),(110,80),(100,140),(45,152)], head=0.24, w=lambda t: profile(t,22,14,18,0.24,0.58), legs=[(0.33,0.75),(0.56,0.75)], gills=0.6, fin=True, spots=0),
 3:dict(ctrl=[(162,55),(100,70),(115,150),(35,160)], head=0.19, w=lambda t: profile(t,20,13,17,0.19,0.53), legs=[(0.27,1.0),(0.48,1.0)], gills=0.0, fin=False, spots=0),
 4:dict(ctrl=None, spine=lambda: curl_spine(), head=0.15, w=lambda t: profile(t,21,13.5,18,0.15,0.36)*(1 if t<0.36 else 1-0.35*(t-0.36)/0.64), legs=[(0.20,1.1),(0.33,1.1)], gills=0.0, fin=False, spots=0),
}
def curl_spine(n=160):
    # 몸통은 오른쪽 위에서 왼쪽 아래로, 꼬리는 몸 아래를 돌아 원(코어 링)을 그리며 감김
    body=[bez([(172,40),(125,50),(140,138),(92,139)],i/60) for i in range(61)]
    cx,cy,R=92,97,42
    a0=math.atan2(body[-1][1]-cy,body[-1][0]-cx)
    arc=[]
    for i in range(1,101):
        a=a0+ (i/100)*math.radians(235)
        r=R*(1-0.12*i/100)
        arc.append((cx+r*math.cos(a),cy+r*math.sin(a)))
    pts=body+arc
    # 균일 간격 재표본
    import bisect
    d=[0]
    for i in range(1,len(pts)): d.append(d[-1]+math.hypot(pts[i][0]-pts[i-1][0],pts[i][1]-pts[i-1][1]))
    out=[]
    for k in range(n+1):
        L=d[-1]*k/n; j=min(bisect.bisect_left(d,L),len(pts)-1)
        out.append(pts[j])
    return out
def profile(t, headW, neckW, bodyW, headEnd, bodyEnd):
    # head: rounded snout -> wide -> neck
    if t<headEnd:
        x=t/headEnd
        if x<0.55: return headW*math.sqrt(max(0,1-((0.55-x)/0.55)**2))
        return headW-(headW-neckW)*((x-0.55)/0.45)**1.5
    if t<bodyEnd:
        x=(t-headEnd)/(bodyEnd-headEnd)
        return neckW+(bodyW-neckW)*math.sin(math.pi*min(1,x*1.1))**0.7 if x<0.9 else neckW+(bodyW-neckW)*math.sin(math.pi*0.99)**0.7*(1-(x-0.9)/0.1*0.3)
    x=(t-bodyEnd)/(1-bodyEnd)
    start=profile(bodyEnd-1e-6,headW,neckW,bodyW,headEnd,bodyEnd)
    return start*(0.30+0.70*(1-x)**1.25)
def build(s, G="#34C77B", D="#1F8A52", L="#7FE3AE", eye="#16241C"):
    st=STAGES[s]; pts=st['spine']() if 'spine' in st else spine_pts(st['ctrl']); nm=normals(pts); n=len(pts)-1
    W=[st['w'](i/n) for i in range(n+1)]
    left=[(p[0]+a*w,p[1]+b*w) for p,(a,b,_,_),w in zip(pts,nm,W)]
    right=[(p[0]-a*w,p[1]-b*w) for p,(a,b,_,_),w in zip(pts,nm,W)]
    body=fmt(left+right[::-1])
    svg=[]
    # tail fin (larva)
    if st['fin']:
        i0=int(n*(st['head']+0.45)); fin_l=[]; fin_r=[]
        for i in range(i0,n+1):
            t=(i-i0)/(n-i0); e=6*math.sin(math.pi*min(1,t*1.05))*(1 if s==1 else 0.6)+0.5
            p=pts[i];a,b,_,_=nm[i]; w=W[i]
            fin_l.append((p[0]+a*(w+e),p[1]+b*(w+e))); fin_r.append((p[0]-a*(w+e),p[1]-b*(w+e)))
        svg.append(f'<path d="{fmt(fin_l+fin_r[::-1])}" fill="{L}" opacity="0.75"/>')
    # ring (stage 4) behind
    if st.get('ring'):
        svg.append(f'<path d="M 78 52 A 40 40 0 1 0 122 52" fill="none" stroke="{L}" stroke-width="7" stroke-linecap="round" opacity="0.55"/>')
    # legs
    for (tl,scale) in st['legs']:
        i=int(n*tl); p=pts[i]; a,b,tx,ty=nm[i]; w=W[i]
        for side in (1,-1):
            sx,sy=p[0]+side*a*w*0.7,p[1]+side*b*w*0.7
            # upper leg outward and slightly back(front leg forward)
            fwd = 1 if tl<0.4 else -1
            kx=sx+side*a*13*scale - tx*4*scale*(-fwd); ky=sy+side*b*13*scale - ty*4*scale*(-fwd)
            fx=kx - tx*7*scale*(-fwd)*-1 + side*a*3*scale; fy=ky - ty*7*scale*(-fwd)*-1 + side*b*3*scale
            svg.append(f'<path d="M{sx:.1f},{sy:.1f} Q{kx:.1f},{ky:.1f} {fx:.1f},{fy:.1f}" fill="none" stroke="{G}" stroke-width="{6.5*scale:.1f}" stroke-linecap="round"/>')
            # toes
            for k in (-1,0,1):
                ang=math.atan2(fy-ky,fx-kx)+k*0.65
                tx2=fx+math.cos(ang)*4.2*scale; ty2=fy+math.sin(ang)*4.2*scale
                svg.append(f'<line x1="{fx:.1f}" y1="{fy:.1f}" x2="{tx2:.1f}" y2="{ty2:.1f}" stroke="{G}" stroke-width="{2.6*scale:.1f}" stroke-linecap="round"/>')
                svg.append(f'<circle cx="{tx2:.1f}" cy="{ty2:.1f}" r="{1.7*scale:.1f}" fill="{G}"/>')
    # gills (external, feathery)
    svg.append(f'<path d="{body}" fill="{G}"/>')
    gs={1:1.35,2:1.15,3:1.0,4:0.95}[s] if s in (1,2,3,4) else st.get('gscale',1.0)
    i=int(n*st['head']*0.78); p=pts[i]; a,b,tx,ty=nm[i]; w=W[i]
    for side in (1,-1):
        bx,by=p[0]+side*a*w*0.82,p[1]+side*b*w*0.82
        for k,deg in enumerate((-5,30,65)):
            r=math.radians(deg)
            dx=side*a*math.cos(r)+tx*math.sin(r); dy=side*b*math.cos(r)+ty*math.sin(r)
            L1=7.5*gs
            cx_,cy_=bx+dx*L1*0.85,by+dy*L1*0.85
            ang=math.degrees(math.atan2(dy,dx))
            svg.insert(0,f'<ellipse cx="{cx_:.1f}" cy="{cy_:.1f}" rx="{L1*0.95:.1f}" ry="{L1*0.48:.1f}" transform="rotate({ang:.1f} {cx_:.1f} {cy_:.1f})" fill="{L}"/>')
    # 둥근 꼬리 끝
    svg.append(f'<circle cx="{pts[-1][0]:.1f}" cy="{pts[-1][1]:.1f}" r="{W[-1]:.1f}" fill="{G}"/>')
    # belly/back highlight stripe
    i0=int(n*st['head']*0.4); i1=int(n*0.8)
    hl=[(p[0]+a*w*0.35,p[1]+b*w*0.35) for p,(a,b,_,_),w in zip(pts[i0:i1],nm[i0:i1],W[i0:i1])]
    svg.append(f'<path d="M'+" L".join(f"{x:.1f},{y:.1f}" for x,y in hl)+f'" fill="none" stroke="{L}" stroke-width="3" stroke-linecap="round" opacity="0.6"/>')
    # spots
    for k in range(st['spots']):
        t=st['head']+0.06+(0.78-st['head'])*k/max(1,st['spots']-1)
        i=int(n*t); p=pts[i]; a,b,_,_=nm[i]; w=W[i]; side=1 if k%2==0 else -1
        r=max(1.6,w*0.28)
        svg.append(f'<circle cx="{p[0]+side*a*w*0.38:.1f}" cy="{p[1]+side*b*w*0.38:.1f}" r="{r:.1f}" fill="{D}"/>')
    # 얼굴: 아이콘과 같은 웃는 실눈(^) · 볼 · 콧구멍 점
    i=int(n*st['head']*0.40); p=pts[i]; a,b,tx,ty=nm[i]; w=W[i]
    fx_,fy_=-tx,-ty
    ux,uy=fx_*0.35,fy_*0.35-1.0; L_=math.hypot(ux,uy); ux,uy=ux/L_,uy/L_   # 눈은 화면 기준 위쪽으로 둥근 ^
    px_,py_=-uy,ux
    er=w*(0.25 if s==1 else 0.22)
    for side in (1,-1):
        ex,ey=p[0]+side*a*w*0.52,p[1]+side*b*w*0.52
        st0=(ex-px_*er-ux*er*0.35, ey-py_*er-uy*er*0.35)
        en0=(ex+px_*er-ux*er*0.35, ey+py_*er-uy*er*0.35)
        c0=(ex+ux*er*1.2, ey+uy*er*1.2)
        svg.append(f'<path d="M{st0[0]:.1f},{st0[1]:.1f} Q{c0[0]:.1f},{c0[1]:.1f} {en0[0]:.1f},{en0[1]:.1f}" fill="none" stroke="{eye}" stroke-width="{er*0.42:.1f}" stroke-linecap="round"/>')
        bl=(ex+side*a*er*0.7-fx_*er*1.9, ey+side*b*er*0.7-fy_*er*1.9)
        svg.append(f'<circle cx="{bl[0]:.1f}" cy="{bl[1]:.1f}" r="{er*0.62:.1f}" fill="#FF97AA" opacity="0.9"/>')
    i=int(n*st['head']*0.06); p=pts[i]; a,b,tx,ty=nm[i]; w=W[i]
    for side in (1,-1):
        nx,ny=p[0]+side*a*w*0.32,p[1]+side*b*w*0.32
        svg.append(f'<circle cx="{nx:.1f}" cy="{ny:.1f}" r="{max(0.9,w*0.07):.1f}" fill="{eye}"/>')
    import re
    allp="".join(svg); nums=[float(x) for x in re.findall(r'-?\d+\.?\d*',allp)]
    xs=[];ys=[]
    for m in re.finditer(r'(?:M|L|Q| )(-?\d+\.\d),(-?\d+\.\d)',allp):
        xs.append(float(m[1])); ys.append(float(m[2]))
    for m in re.finditer(r'c?x\d?="(-?[\d.]+)" c?y\d?="(-?[\d.]+)"',allp):
        xs.append(float(m[1])); ys.append(float(m[2]))
    x0,x1,y0,y1=min(xs),max(xs),min(ys),max(ys); side=max(x1-x0,y1-y0)+12
    cx,cy=(x0+x1)/2,(y0+y1)/2
    return f'<svg viewBox="{cx-side/2:.1f} {cy-side/2:.1f} {side:.1f} {side:.1f}" xmlns="http://www.w3.org/2000/svg">{allp}</svg>'
if __name__=="__main__":
    json.dump({s:build(s) for s in STAGES}, open('/tmp/claude-0/sal2/svgs.json','w'))
    html="<html><body style='background:#f4f6f5;margin:0'>"+"".join(f"<div style='display:inline-block;width:280px;height:280px'>{build(s)}</div>" for s in STAGES)+"<br>"+"".join(f"<div style='display:inline-block;width:32px;height:32px;margin:10px;background:#fff'>{build(s)}</div>" for s in STAGES)+"</body></html>"
    open('/tmp/claude-0/sal2/p.html','w').write(html)
