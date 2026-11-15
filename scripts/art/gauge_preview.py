import math
CX,CY,R,T=100,104,50,26   # 링 중심·반지름·두께
TIER={'stable':dict(body="#2FCB72",rim="#1E9E56",stem="#34C77B",gill="#8FF0BE",face='happy'),
      'fair':dict(body="#2FCB72",rim="#1E9E56",stem="#F4C430",gill="#8FF0BE",face='happy'),
      'caution':dict(body="#8DBF3F",rim="#5F8F2A",stem="#F0932B",gill="#F2A23A",face='worry'),
      'rest':dict(body="#9A7A45",rim="#6E5530",stem="#E8533F",gill="#F27B6B",face='tired')}
RED="#E84C3C"; DARK="#1B2A22"
def tier_of(s): return 'stable' if s>=80 else 'fair' if s>=60 else 'caution' if s>=40 else 'rest'
def P(a,r=R): t=math.radians(a); return (CX+r*math.cos(t),CY+r*math.sin(t))
def arc(a0,a1,col,w,r=R):
    if a1-a0<0.5: 
        p=P(a0,r); return f'<circle cx="{p[0]:.1f}" cy="{p[1]:.1f}" r="{w/2:.1f}" fill="{col}"/>'
    p0=P(a0,r);p1=P(a1,r); large=1 if a1-a0>180 else 0
    return f'<path d="M{p0[0]:.2f},{p0[1]:.2f} A{r},{r} 0 {large} 1 {p1[0]:.2f},{p1[1]:.2f}" fill="none" stroke="{col}" stroke-width="{w}" stroke-linecap="round"/>'
def gauge(score, bg=None):
    t=TIER[tier_of(score)]
    head=-60+300*max(score,8)/100          # 입 위치(각도)
    neck=head-9
    s=[]
    s.append(arc(-60,240,RED,T))                         # 빨간 링 (아직 못 먹은 부분)
    # 다리 (바깥쪽, 몸 아래)
    for off in (16, min(70, (head+60)*0.6)):
        a=neck-off; p=P(a,R+T*0.42)
        s.append(f'<ellipse cx="{p[0]:.1f}" cy="{p[1]:.1f}" rx="{T*0.26:.1f}" ry="{T*0.17:.1f}" transform="rotate({a+90+20:.1f} {p[0]:.1f} {p[1]:.1f})" fill="{t["rim"]}"/>')
    s.append(arc(-60,neck,t['rim'],T))                   # 몸 (바깥 그림자)
    s.append(arc(-60,neck,t['body'],T*0.8,R-T*0.08))     # 몸
    s.append(arc(-50,neck-14,"#FFFFFF",T*0.12,R-T*0.22).replace('stroke="#FFFFFF"','stroke="#FFFFFF" stroke-opacity="0.35"'))  # 등줄기 선
    # 머리: 입 지점 기준 좌표계 (x: 진행 방향, y: 링 바깥쪽)
    mx,my=P(head); trav=head+90
    g=[]
    # 아가미 꽃잎 (머리 뒤, 등쪽=안쪽)
    for k,(gx,gy,ga) in enumerate(((-1.05,-0.95,-130),(-1.3,-0.72,-150),(-1.42,-0.45,-170))):
        g.append(f'<ellipse cx="{gx*T:.1f}" cy="{gy*T:.1f}" rx="{T*0.34:.1f}" ry="{T*0.17:.1f}" transform="rotate({ga} {gx*T:.1f} {gy*T:.1f})" fill="{t["gill"]}"/>')
    g.append(f'<ellipse cx="{-0.42*T:.1f}" cy="{-0.05*T:.1f}" rx="{0.78*T:.1f}" ry="{0.66*T:.1f}" transform="rotate(-8 {-0.42*T:.1f} {-0.05*T:.1f})" fill="{t["body"]}"/>')
    # 입 (벌린 입, 앞쪽)
    g.append(f'<ellipse cx="{-0.02*T:.1f}" cy="{0.02*T:.1f}" rx="{0.30*T:.1f}" ry="{0.36*T:.1f}" transform="rotate(-10 {-0.02*T:.1f} {0.02*T:.1f})" fill="{DARK}"/>')
    g.append(f'<circle cx="{0.10*T:.1f}" cy="{0.0:.1f}" r="{0.14*T:.1f}" fill="{RED}"/><circle cx="{-0.12*T:.1f}" cy="{0.14*T:.1f}" r="{0.06*T:.1f}" fill="#F7A65A"/><circle cx="{-0.1*T:.1f}" cy="{-0.14*T:.1f}" r="{0.05*T:.1f}" fill="#6FE3A5"/>')      # 먹고 있는 빨간 조각
    # 눈
    ex,ey=-0.62*T,-0.36*T
    if t['face']=='happy':
        g.append(f'<path d="M{ex-0.16*T:.1f},{ey-0.16*T:.1f} L{ex+0.1*T:.1f},{ey:.1f} L{ex-0.16*T:.1f},{ey+0.16*T:.1f}" fill="none" stroke="{DARK}" stroke-width="{0.1*T:.1f}" stroke-linecap="round" stroke-linejoin="round"/>')
    elif t['face']=='worry':
        g.append(f'<circle cx="{ex:.1f}" cy="{ey:.1f}" r="{0.13*T:.1f}" fill="{DARK}"/><circle cx="{ex+0.04*T:.1f}" cy="{ey-0.05*T:.1f}" r="{0.05*T:.1f}" fill="#fff"/>')
        g.append(f'<path d="M{ex-0.2*T:.1f},{ey-0.33*T:.1f} L{ex+0.12*T:.1f},{ey-0.22*T:.1f}" stroke="{DARK}" stroke-width="{0.08*T:.1f}" stroke-linecap="round"/>')
    else:
        g.append(f'<path d="M{ex-0.18*T:.1f},{ey:.1f} Q{ex-0.03*T:.1f},{ey+0.14*T:.1f} {ex+0.12*T:.1f},{ey:.1f}" fill="none" stroke="{DARK}" stroke-width="{0.09*T:.1f}" stroke-linecap="round"/>')
    g.append(f'<circle cx="{-0.1*T:.1f}" cy="{-0.52*T:.1f}" r="{0.05*T:.1f}" fill="{DARK}"/>')   # 콧구멍
    g.append(f'<circle cx="{-0.88*T:.1f}" cy="{0.12*T:.1f}" r="{0.13*T:.1f}" fill="#FF97AA" opacity="0.85"/>')  # 볼
    s.append(f'<g transform="translate({mx:.1f},{my:.1f}) rotate({trav:.1f}) scale(1,-1)">{"".join(g)}</g>')
    # 막대
    s.append(f'<line x1="{CX}" y1="{CY-R-T*0.55:.1f}" x2="{CX}" y2="{CY-R*0.3:.1f}" stroke="{t["stem"]}" stroke-width="{T*0.82:.1f}" stroke-linecap="round"/>')
    rect=f'<rect x="20" y="20" width="160" height="168" fill="{bg}"/>' if bg else ''
    return f'<svg viewBox="22 20 156 160" xmlns="http://www.w3.org/2000/svg">{rect}{"".join(s)}</svg>'
if __name__=="__main__":
    scores=[95,85,75,65,55,45,35,15]
    cells="".join(f"<div class=c>{gauge(s)}<div>{s}</div></div>" for s in scores)
    small="".join(f"<span>{gauge(s)}</span>" for s in scores)
    html=f"<html><head><meta charset=utf-8><style>body{{margin:16px;font-family:sans-serif;background:#fff}}.c{{display:inline-block;text-align:center;width:150px}}.c svg{{width:140px;height:144px}}span svg{{width:54px;height:56px}}span{{display:inline-block;margin:6px}}.dk{{background:#1d2420;padding:8px;border-radius:10px}}</style></head><body><div>{cells}</div><div>{small}</div><div class=dk>{small}</div></body></html>"
    open('vg.html','w').write(html)
