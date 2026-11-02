"""Deterministic original Cangsheng cues, 2026-10-27.

No source audio is opened. Animal vocal-fold/formant synthesis, tuned crystal
resonators, a descending physical low hit, and breath-excited flute oscillators.
The video is used solely by the separate read-only reference review script.
"""
from pathlib import Path
import hashlib,json,math,subprocess,wave
import numpy as np
from PIL import Image,ImageDraw,ImageFont

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'verification/axolotl-20261027/audio'
ASSETS=ROOT/'src/main/resources/assets/two_i_combat_doll'
FF=ROOT.parent/'DollSkinBlinkTool/verification/media_tools/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe'
RATE=48000
OUT.mkdir(parents=True,exist_ok=True)
(ASSETS/'sounds').mkdir(exist_ok=True)

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def axis(seconds):return np.arange(round(seconds*RATE))/RATE
def smooth(x):
    x=np.clip(x,0,1);return x*x*(3-2*x)
def window(t,start,hold,attack,release):
    return smooth((t-start)/attack)*smooth((start+hold+release-t)/release)
def noise(t,seed,low,high):
    x=np.random.default_rng(seed).normal(0,1,len(t));f=np.fft.rfftfreq(len(t),1/RATE)
    shape=1/(1+(low/np.maximum(f,.001))**8)/(1+(f/high)**8)
    x=np.fft.irfft(np.fft.rfft(x)*shape,len(t));return x/max(np.std(x),1e-12)
def delayed(x,seconds):
    n=round(RATE*seconds);y=np.zeros_like(x)
    if n<len(x):y[n:]=x[:len(x)-n]
    return y
def wavefile(path,x):
    with wave.open(str(path),'wb') as out:
        out.setnchannels(1);out.setsampwidth(2);out.setframerate(RATE)
        out.writeframes(np.round(x*32767).astype('<i2').tobytes())
def decode(path):
    result=subprocess.run([str(FF),'-v','error','-i',str(path),'-f','f32le','-ac','1','-ar',str(RATE),'pipe:1'],capture_output=True,check=True)
    return np.frombuffer(result.stdout,dtype='<f4').astype(np.float64)
def db(x):return 20*math.log10(max(float(x),1e-12))
def finalize(name,x,recipe):
    t=np.arange(len(x))/RATE
    x=np.asarray(x,dtype=np.float64);x-=x.mean()
    # No hard clipping or automatic loudness pumping. A finite 8-ms edge and
    # 90-ms tail guarantee silence at asset boundaries.
    x*=smooth(t/.008)*smooth((len(x)/RATE-t)/.09)
    x*=.81/max(np.abs(x).max(),1e-12)
    wav=OUT/(name+'.wav');ogg=ASSETS/'sounds'/(name+'.ogg');wavefile(wav,x)
    subprocess.run([str(FF),'-v','error','-i',str(wav),'-c:a','libvorbis','-q:a','6','-y',str(ogg)],check=True)
    actual=decode(ogg);interp=np.fft.irfft(np.fft.rfft(actual),len(actual)*4)*4
    f=np.fft.rfftfreq(len(actual),1/RATE);power=abs(np.fft.rfft(actual))**2;total=float(power.sum())
    record={'name':name,'origin':'Original deterministic synthesis; no imported recording, sample, melody or video audio.','license':'Project license (see LICENSE); original synthesis authored for this project.','cost':0,'duration_seconds':len(actual)/RATE,'sample_rate':RATE,'channels':1,'wav':str(wav.relative_to(ROOT)),'wav_sha256':sha(wav),'ogg':str(ogg.relative_to(ROOT)),'ogg_sha256':sha(ogg),'recipe':recipe,'encoded_peak_dbfs':round(db(abs(actual).max()),3),'true_peak_4x_dbfs':round(db(abs(interp).max()),3),'rms_dbfs':round(db(np.sqrt(np.mean(actual*actual))),3),'20_160_hz_energy_fraction':round(float(power[(f>=20)&(f<160)].sum())/total,5),'160_2000_hz_energy_fraction':round(float(power[(f>=160)&(f<2000)].sum())/total,5),'clipped_sample_count':int(np.count_nonzero(abs(actual)>=.999)),'dc_offset':round(float(actual.mean()),9)}
    assert record['clipped_sample_count']==0 and record['true_peak_4x_dbfs']<-.6,record
    return record,x

records=[];signals={}
# Dreamy mountain emergence: rising major-sixth crystal resonators above a
# velvet C3/G3 foundation. Not thunder and not the existing dark two-i cue.
t=axis(4.6);awaken=np.zeros_like(t)
for i,(start,hz,gain) in enumerate([(0,261.6256,.45),(.21,391.9954,.30),(.48,523.2511,.35),(.79,659.2551,.26),(1.14,783.9909,.22),(1.57,1046.5023,.17)]):
    local=np.maximum(t-start,0);envelope=(1-np.exp(-local*18))*np.exp(-local/(1.65-i*.10));envelope[t<start]=0
    tone=np.sin(math.tau*hz*local)+.22*np.sin(math.tau*hz*2.002*local)+.085*np.sin(math.tau*hz*3.98*local)
    awaken+=tone*envelope*gain
pad=(np.sin(math.tau*130.8128*t)+.34*np.sin(math.tau*196*t)+.12*np.sin(math.tau*261.6256*t))
awaken+=pad*.25*window(t,0,2.9,.7,1.6)
awaken+=noise(t,2710271,650,3200)*.011*window(t,0,2.4,.8,1.2)
awaken+=delayed(awaken,.19)*.16+delayed(awaken,.41)*.09
records.append(finalize('cangsheng_awaken',awaken,{'design':'Bright pearlescent mountain reveal; C/G/E/A crystal modes, small air excitation, long but quiet resonant decay.','notes_hz':[261.6256,391.9954,523.2511,659.2551,783.9909,1046.5023],'noise_seed':2710271})[0]);signals['cangsheng_awaken']=awaken

# Creature roar: articulated inhalation -> open-throated voiced call -> low
# falling growl. Moving harmonic formants produce a biological vowel, rather
# than stacking explosion samples or a continuous broadband noise bed.
t=axis(3.35)
f0=118-57*smooth((t-.22)/2.5)+7*np.sin(math.tau*5.2*t)*window(t,.22,2.1,.15,.6)
phase=math.tau*np.cumsum(f0)/RATE
voice=np.zeros_like(t)
formants=[(420+170*np.sin(math.pi*np.clip(t/2.8,0,1)),145,1.0),(1070-230*smooth(t/2.8),200,.68),(2080,290,.24)]
for harmonic in range(1,45):
    freq=f0*harmonic
    weight=.075/harmonic**1.15
    for centre,width,gain in formants:weight+=gain*np.exp(-.5*((freq-centre)/width)**2)/harmonic**.76
    voice+=weight*np.sin(harmonic*phase+.12*math.sin(harmonic*2.7))
voiced_envelope=window(t,.15,2.15,.13,.83)
roughness=.78+.13*np.sin(math.tau*25*t+.4*np.sin(math.tau*2*t))+.09*np.sin(math.tau*37.8*t)
roar=voice*roughness*voiced_envelope
roar+=np.sin(phase*.5)*.34*voiced_envelope
roar+=noise(t,2710272,180,1700)*.032*window(t,.035,2.0,.12,.7)
roar+=noise(t,2710273,550,2500)*.025*window(t,0,.13,.02,.1)
roar+=delayed(roar,.127)*.15+delayed(roar,.283)*.075
records.append(finalize('cangsheng_roar',roar,{'design':'Original enormous but living animal call: breath intake, harmonically voiced open-throat roar, falling growl; no copied animal recording.','fundamental_hz':'118 -> 61 with 5.2-Hz slight vocal flutter','formants_hz':'moving420–590,1070–840,2080','noise_seeds':[2710272,2710273],'biological_note':'Fantasy amphibian vocal design, not a claim about real axolotl calls.'})[0]);signals['cangsheng_roar']=roar

# Body slam: an immediate transient, clean descending sub body and small
# finite fragments; avoid a long rushing noise tail obscuring the landing.
t=axis(1.9)
phase=math.tau*(41*t+(145-41)*.055*(1-np.exp(-t/.055)))
impact=(np.sin(phase)+.17*np.sin(phase*2))*np.exp(-t/.39)
impact+=.33*np.sin(math.tau*(34*t+35*.09*(1-np.exp(-t/.09))))*np.exp(-t/.67)
impact+=noise(t,2710274,420,5800)*.48*np.exp(-t/.017)
impact+=.48*np.sin(math.tau*(235*t+55*.018*(1-np.exp(-t/.018))))*np.exp(-t/.115)
impact+=.21*np.sin(math.tau*628*t)*np.exp(-t/.055)
for i,(start,hz) in enumerate([(0,184),(.017,307),(.039,467),(.077,789),(.129,1213)]):
    local=np.maximum(t-start,0);env=(1-np.exp(-local*1600))*np.exp(-local/(.06+i*.016));env[t<start]=0
    impact+=.20/(1+i*.35)*np.sin(math.tau*hz*local)*env
impact+=delayed(impact,.113)*.15+delayed(impact,.247)*.065
records.append(finalize('cangsheng_impact',impact,{'design':'Short hard mountain-body landing: 145->41-Hz center hit, 34-Hz weight, 235/628-Hz punch, five finite crystal/stone modes, 17-ms bandlimited crack.','noise_seed':2710274,'impact_onset_seconds':0,'tail':'Two quiet finite echoes; no sustained rumble/noise bed.'})[0]);signals['cangsheng_impact']=impact

# A tiny pentatonic birdlike bamboo-flute phrase, freshly composed. Near
# sinusoidal air column with weak second/third harmonics and breath envelope.
t=axis(3.0);flute=np.zeros_like(t)
for i,(start,duration,hz) in enumerate([(0,.53,783.9909),(.43,.61,1046.5023),(1.00,1.30,880.0)]):
    local=np.maximum(t-start,0);env=window(t,start,duration,.06,.29)
    vib=.0024*np.sin(math.tau*5.0*local)*smooth(local/.3)
    phase=math.tau*np.cumsum(hz*(1+vib))/RATE
    tone=np.sin(phase)+.075*np.sin(phase*2)+.025*np.sin(phase*3)
    flute+=tone*env*(.70 if i<2 else .62)
    flute+=noise(t,2710275+i,1100,4800)*env*.006
flute+=delayed(flute,.145)*.13+delayed(flute,.31)*.045
records.append(finalize('cangsheng_flute',flute,{'design':'Original gentle three-note bamboo/ocarina-style recall phrase, G5/C6/A5, tiny 5-Hz breath vibrato, quiet air-column harmonics.','notes_hz':[783.9909,1046.5023,880.0],'noise_seeds':[2710275,2710276,2710277]})[0]);signals['cangsheng_flute']=flute

# A listening-only sequence makes boundaries and cue contrast inspectable.
preview=np.zeros(18*RATE)
for name,start in [('cangsheng_awaken',0),('cangsheng_roar',5.0),('cangsheng_impact',9.0),('cangsheng_flute',12.0)]:
    x=decode(ASSETS/'sounds'/(name+'.ogg'));n=round(start*RATE);preview[n:n+len(x)]+=x
wavefile(OUT/'cangsheng-cues-preview.wav',preview)
audit={'generator':str(Path(__file__).relative_to(ROOT)),'generator_sha256':sha(Path(__file__)),'created_date':'2026-10-27','source_recordings':[],'source_video_audio_used':False,'paid_assets':False,'sample_rate':RATE,'production_format':'Mono Ogg Vorbis for positional Minecraft sound; q=6','cues':records,'listening_preview':str((OUT/'cangsheng-cues-preview.wav').relative_to(ROOT)),'limits':'Encoding, peak/headroom and spectrum validated. Listening/actual client mix is still required; numeric audit is not subjective listening.'}
(OUT/'audio-audit.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2)+'\n',encoding='utf8')
credits='Cangsheng Axolotl audio — 2026-10-27\n\nOriginal deterministic synthesis, authored for this project.\nNo samples, recordings, melodies, or audio from the reference video were imported.\nNo paid assets and no third-party recording license is required.\nProject license: see root LICENSE.\n\nGenerator: tools/synth/original-cangsheng-audio.py\nReproducible recipes and encoded SHA-256/peak audit:\nverification/axolotl-20261027/audio/audio-audit.json\n\nAssets: cangsheng_awaken.ogg, cangsheng_roar.ogg, cangsheng_impact.ogg, cangsheng_flute.ogg\nThe creature call is a fantasy voiced formant design, not a recording of real axolotl behavior.\n'
(ASSETS/'CANGSHENG_SOUND_CREDITS.txt').write_text(credits,encoding='utf8')
font=ImageFont.truetype(r'C:\Windows\Fonts\consola.ttf',18)
image=Image.new('RGB',(1400,700),'#172031');d=ImageDraw.Draw(image)
d.text((25,15),'CANGSHENG / ORIGINAL SYNTHESIS / MONO GAME CUES',font=font,fill='#f8eafa')
for i,record in enumerate(records):
    x=decode(ROOT/record['ogg']);y=112+i*145
    d.text((25,y-50),f"{record['name']}  {record['duration_seconds']:.2f}s   peak {record['encoded_peak_dbfs']}dBFS   truepeak {record['true_peak_4x_dbfs']}dBFS",font=font,fill='#f2c2ec')
    for col in range(1320):
        a=math.floor(col*len(x)/1320);b=max(a+1,math.floor((col+1)*len(x)/1320));mag=float(abs(x[a:b]).max())
        d.line((40+col,y-mag*45,40+col,y+mag*45),fill='#9bdfde')
image.save(OUT/'cangsheng-waveforms.png')
print(json.dumps(records,ensure_ascii=False,indent=2))
