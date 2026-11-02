"""Clean CC0 impact candidates. No production write unless --promote A/B.

Kenney's source files retain CC0 provenance. Our additions are a short centred
oscillator sub hit, sparse clock ticks and two quiet reflections, not noise beds.
"""
from pathlib import Path
import argparse
import hashlib
import json
import math
import shutil
import subprocess
import wave
import zipfile

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/"verification/astral-20261027/audio/v4-candidate"
A=ROOT/"src/main/resources/assets/two_i_combat_doll"
FF=ROOT.parent/"DollSkinBlinkTool/verification/media_tools/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe"
RATE=48000
OUT.mkdir(parents=True,exist_ok=True)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def decode(path,channels=1):
    raw=subprocess.run([str(FF),"-v","error","-i",str(path),"-f","f32le","-ac",str(channels),"-ar",str(RATE),"pipe:1"],capture_output=True,check=True).stdout
    x=np.frombuffer(raw,dtype="<f4").astype(np.float64)
    return x if channels==1 else x.reshape(-1,channels)
def band(x,high):
    f=np.fft.rfftfreq(len(x),1/RATE);return np.fft.irfft(np.fft.rfft(x)/(1+(f/high)**8),len(x))
def pitch_down(x,ratio):
    return np.interp(np.arange(round(len(x)/ratio))*ratio,np.arange(len(x)),x)
def wavefile(path,x):
    with wave.open(str(path),"wb") as w:
        w.setnchannels(1 if x.ndim==1 else x.shape[1]);w.setsampwidth(2);w.setframerate(RATE)
        w.writeframes(np.round(np.clip(x,-.999,.999)*32767).astype("<i2").tobytes())
def db(x):return 20*math.log10(max(float(x),1e-12))
def rms(x):return float(np.sqrt(np.mean(x*x)))
def truepeak(x):return max(float(abs(np.fft.irfft(np.fft.rfft(x[:,i]),len(x)*4)*4).max()) for i in range(2))

sources=[]
for pack,name in (("scifi","Audio/lowFrequency_explosion_000.ogg"),("impact","Audio/impactBell_heavy_002.ogg")):
    stem="sci-fi-sounds" if pack=="scifi" else "impact-sounds"
    archive=OUT/f"kenney_{stem}.zip";asset=OUT/"sources"/pack/name;license_path=OUT/"sources"/pack/"License.txt"
    with zipfile.ZipFile(archive) as z:
        assert asset.read_bytes()==z.read(name)
        assert license_path.read_bytes()==z.read("License.txt")
    text=license_path.read_text(encoding="utf8")
    assert "CC0" in text and "commercial" in text
    sources.append({"author":"Kenney","url":f"https://kenney.nl/assets/{stem}","license":"CC0-1.0",
        "license_url":"https://creativecommons.org/publicdomain/zero/1.0/","license_name":"License.txt",
        "zip_path":str(archive.relative_to(ROOT)),"zip_sha256":sha(archive),"asset_name":name,
        "asset_path":str(asset.relative_to(ROOT)),"asset_sha256":sha(asset),
        "license_path":str(license_path.relative_to(ROOT)),"license_sha256":sha(license_path),"cost":0})

parser=argparse.ArgumentParser();parser.add_argument("--promote",choices=("A","B"));args=parser.parse_args()
if args.promote:
    p=OUT/f"time-stop-v4-{args.promote}-provenance.json";record=json.loads(p.read_text(encoding="utf8"))
    source=ROOT/record["ogg"];assert sha(source)==record["sha256"]
    target=A/"sounds/time_stop.ogg";shutil.copyfile(source,target)
    record.update({"production_selected":True,"candidate_ogg":record["ogg"],"ogg":str(target.relative_to(ROOT)),"sha256":sha(target)})
    active=ROOT/"verification/astral-20261027/audio/original-declaration-provenance.json"
    active.write_text(json.dumps(record,ensure_ascii=False,indent=2)+"\n",encoding="utf8")
    (active.parent/"licensed-declaration-provenance.json").write_text(json.dumps(record,ensure_ascii=False,indent=2)+"\n",encoding="utf8")
    audit_path=active.parent/"audio-audit.json";audit=json.loads(audit_path.read_text(encoding="utf8"))
    audit["synthesis"]="time_stop is an adaptation of two Kenney CC0 samples with original sub/clock/reflection processing. The other four astral effects remain original synthesis."
    audit["encoded_assets"]["time_stop"]=record;audit_path.write_text(json.dumps(audit,ensure_ascii=False,indent=2)+"\n",encoding="utf8")
    print("PROMOTED",args.promote,record["sha256"]);raise SystemExit

explosion=decode(ROOT/sources[0]["asset_path"]);explosion-=explosion.mean()
explosion=band(explosion,1300);explosion/=max(abs(explosion).max(),1e-12)
bell=decode(ROOT/sources[1]["asset_path"]);bell-=bell.mean()
bell=band(pitch_down(bell,.58),1250);bell/=max(abs(bell).max(),1e-12)

records=[]
for choice,duration in (("A",3.4),("B",4.4)):
    t=np.arange(round(RATE*duration))/RATE
    stereo=np.zeros((len(t),2))
    def add(x,seconds,gain=1,pan=0):
        n=round(seconds*RATE);count=min(len(x),len(t)-n)
        if count>0:stereo[n:n+count,0]+=x[:count]*gain*(1-max(0,pan)*.25);stereo[n:n+count,1]+=x[:count]*gain*(1+min(0,pan)*.25)
    # A clean 70->42 Hz punch, a restrained octave and a finite decay. No
    # randomized noise, dense crack layer, scrape or AGC sustained noise tail.
    phase=math.tau*(42*t+(70-42)*.105*(1-np.exp(-t/.105)))
    sub=(np.sin(phase)+.16*np.sin(phase*2))*.56*np.exp(-t/.64)
    sub*=1-np.exp(-t*850)
    add(sub,0);add(explosion,0,.72)
    add(band(explosion,460),.115,.075,-.5);add(band(explosion,330),.291,.042,.5)
    used=sources[:1]
    if choice=="B":
        add(bell,.026,.28);add(band(bell,700),.338,.065,-.5);add(band(bell,550),.681,.035,.5)
        used=sources
    for i,seconds in enumerate((1.05,2.17) if choice=="A" else (1.05,2.17,3.29)):
        local=np.maximum(t-seconds,0)
        tick=(np.sin(math.tau*(1177-i*83)*local)*.026*np.exp(-local*65)+np.sin(math.tau*173*local)*.018*np.exp(-local*34))
        tick*=1-np.exp(-local*1400);tick[t<seconds]=0;add(tick,0)
    fade=np.sin(np.clip(t/.001,0,1)*math.pi/2)**2*np.sin(np.clip((duration-t)/.20,0,1)*math.pi/2)**2
    stereo*=fade[:,None];stereo-=stereo.mean(axis=0)
    stereo=np.tanh(stereo*1.14)/1.14
    stereo*=.83/max(abs(stereo).max(),1e-12)
    wav=OUT/f"time-stop-v4-{choice}.wav";ogg=OUT/f"time-stop-v4-{choice}.ogg";wavefile(wav,stereo)
    subprocess.run([str(FF),"-v","error","-i",str(wav),"-c:a","libvorbis","-q:a","7","-y",str(ogg)],check=True)
    actual=decode(ogg,2);mono=actual.mean(axis=1);freq=np.fft.rfftfreq(len(mono),1/RATE);power=abs(np.fft.rfft(mono))**2;total=float(power.sum())
    record={"version":f"licensed-clean-impact-v4-{choice}","production_selected":False,"duration_seconds":duration,"sample_rate":RATE,"channels":2,
        "origin":"Kenney CC0 impact sample adaptation plus original sub hit, sparse clock and short reflections; not 100% original sound recording.",
        "generator":str(Path(__file__).relative_to(ROOT)),"generator_sha256":sha(Path(__file__)),"license_sources":used,
        "ogg":str(ogg.relative_to(ROOT)),"wav":str(wav.relative_to(ROOT)),"sha256":sha(ogg),
        "peak_dbfs":round(db(abs(actual).max()),3),"true_peak_4x_dbfs":round(db(truepeak(actual)),3),"rms_dbfs":round(db(rms(actual)),3),
        "first_0_8_seconds_rms_dbfs":round(db(rms(actual[:round(.8*RATE)])),3),
        "20_160_hz_energy_fraction":round(float(power[(freq>=20)&(freq<160)].sum())/total,5),
        "160_1000_hz_energy_fraction":round(float(power[(freq>=160)&(freq<1000)].sum())/total,5),
        "1000_8000_hz_energy_fraction":round(float(power[(freq>=1000)&(freq<8000)].sum())/total,5),
        "peak_samples_at_or_above_0_999":int(np.count_nonzero(abs(actual)>=.999)),"dc_offset":round(float(actual.mean()),8),
        "recipe":{"main_onset_seconds":0,"low_hit":"Kenney lowFrequency_explosion_000, lowpass1300Hz, normalized, gain0.72","sub":"original 70->42Hz, octave0.16, decay0.64s","bell":"none" if choice=="A" else "Kenney impactBell_heavy_002, pitch0.58, lowpass1250Hz, gain0.28, onset0.026s","tail":"two quiet lowpassed reflection taps; no sustained noise bed","clock":"2 or 3 original quiet ticks"},
        "limits":"Licenses and signal measures checked; subjective listening is still required. Production remains v3 until explicit selection."}
    assert record["peak_samples_at_or_above_0_999"]==0 and record["true_peak_4x_dbfs"]<-1.0,record
    (OUT/f"time-stop-v4-{choice}-provenance.json").write_text(json.dumps(record,ensure_ascii=False,indent=2)+"\n",encoding="utf8")
    records.append(record)
image=Image.new("RGB",(1400,570),"#0b1020");d=ImageDraw.Draw(image);font=ImageFont.truetype(r"C:\Windows\Fonts\consola.ttf",20)
d.text((30,20),"CC0 CLEAN IMPACT CANDIDATES / PRODUCTION NOT REPLACED",fill="#f1f2ff",font=font)
for row,record in enumerate(records):
    x=decode(ROOT/record["ogg"],2).mean(axis=1);y=150+row*240
    d.text((30,y-75),f"{record['version']}  {record['duration_seconds']}s / first0.8s {record['first_0_8_seconds_rms_dbfs']}dBFS / LF {record['20_160_hz_energy_fraction']:.1%}",fill="#c5bdff",font=font)
    for col in range(1320):
        a=math.floor(col*len(x)/1320);b=max(a+1,math.floor((col+1)*len(x)/1320));mag=float(abs(x[a:b]).max())
        d.line((40+col,y-mag*75,40+col,y+mag*75),fill="#80dcff")
d.text((30,530),"A: clean hit + sub     B: same hit + low bell    Sources: Kenney / CC0-1.0 / zero cost",fill="#b9d6e5",font=font)
image.save(OUT/"v4-candidates-waveforms.png")
print(json.dumps([{k:v for k,v in r.items() if k not in ("license_sources","recipe")} for r in records],ensure_ascii=False,indent=2))
