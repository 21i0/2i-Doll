"""Read-only reference review. Extracted media is evidence, never a game asset."""
from pathlib import Path
import hashlib,json,math,subprocess
from PIL import Image,ImageDraw,ImageFont

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'verification/axolotl-20261027/reference-review'
SOURCE=Path(r'C:\Users\Lenovo\Downloads\video_当溺尸变成神级泰坦？掌控黑洞雷霆..._0.mp4')
FF=ROOT.parent/'DollSkinBlinkTool/verification/media_tools/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe'
OUT.mkdir(parents=True,exist_ok=True)
font=ImageFont.truetype(r'C:\Windows\Fonts\consola.ttf',20)
frames=OUT/'overview-frames';frames.mkdir(exist_ok=True)
subprocess.run([str(FF),'-v','error','-i',str(SOURCE),'-vf','fps=1/3,scale=384:216','-q:v','3','-y',str(frames/'frame-%03d.jpg')],check=True)
files=sorted(frames.glob('frame-*.jpg'))
for page,start in enumerate(range(0,len(files),20)):
    subset=files[start:start+20]
    contact=Image.new('RGB',(1536,38+248*math.ceil(len(subset)/4)),'#10182c');d=ImageDraw.Draw(contact)
    d.text((12,7),'USER VIDEO / READ-ONLY ANIMATION REFERENCE / source seconds',font=font,fill='white')
    for i,path in enumerate(subset):
        x=i%4*384;y=38+i//4*248
        contact.paste(Image.open(path).convert('RGB'),(x,y));d.text((x+8,y+220),f'{(start+i)*3:.1f}s',font=font,fill='white')
    contact.save(OUT/f'overview-{page+1:02d}.png')
record={'source':str(SOURCE),'source_sha256':hashlib.sha256(SOURCE.read_bytes()).hexdigest(),'source_size_bytes':SOURCE.stat().st_size,'duration_seconds':133.38,'width':1280,'height':720,'fps':30,'source_usage':'Reference timing/shape analysis only; no pixels, model, or audio copied into game assets.','overview_seconds':list(range(0,len(files)*3,3))}
record['detail_segments']=[]
for label,start,duration in [('summon',20,14),('attack',43,8),('finale',78,13)]:
    frame_dir=OUT/(label+'-frames');frame_dir.mkdir(exist_ok=True)
    subprocess.run([str(FF),'-v','error','-ss',str(start),'-i',str(SOURCE),'-t',str(duration),'-vf','fps=2,scale=384:216','-q:v','3','-y',str(frame_dir/'frame-%03d.jpg')],check=True)
    subset=sorted(frame_dir.glob('frame-*.jpg'))
    contact=Image.new('RGB',(1536,38+248*math.ceil(len(subset)/4)),'#10182c');d=ImageDraw.Draw(contact)
    d.text((12,7),f'REFERENCE {label.upper()} / 2 fps / source seconds',font=font,fill='white')
    for i,path in enumerate(subset):
        x=i%4*384;y=38+i//4*248
        contact.paste(Image.open(path).convert('RGB'),(x,y));d.text((x+8,y+220),f'{start+i*.5:.1f}s',font=font,fill='white')
    contact.save(OUT/(label+'-contact.png'))
    record['detail_segments'].append({'label':label,'start':start,'duration':duration,'fps':2,'frames':len(subset)})
(OUT/'source-manifest.json').write_text(json.dumps(record,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
print(json.dumps(record,ensure_ascii=False,indent=2))
