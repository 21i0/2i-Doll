"""Original clean mountain-guardian awakening and chest roar, revision 2.

Only these two production cues are written. No sample, video, recording,
online media or melody is read. The unchanged impact/flute and two-i B cue
are hashed before and after. This script never re-runs the old four-cue tool.
"""
from pathlib import Path
import hashlib,json,math,subprocess,wave
import numpy as np

ROOT=Path(__file__).resolve().parents[2]
ASSETS=ROOT/'src/main/resources/assets/two_i_combat_doll'
OUT=ROOT/'verification/axolotl-20261027/audio-v2'
FF=ROOT.parent/'DollSkinBlinkTool/verification/media_tools/imageio_ffmpeg/binaries/ffmpeg-win-x86_64-v7.1.exe'
RATE=48000
OUT.mkdir(parents=True,exist_ok=True)
KEEP=['cangsheng_impact.ogg','cangsheng_flute.ogg','time_stop.ogg']

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def axis(seconds):return np.arange(round(seconds*RATE))/RATE
def smooth(x):
    x=np.clip(x,0,1);return x*x*(3-2*x)
def gate(t,start,attack,hold,release):
    return smooth((t-start)/attack)*smooth((start+attack+hold+release-t)/release)
def delayed(x,seconds):
    n=round(seconds*RATE);y=np.zeros_like(x)
    if n<len(x):y[n:]=x[:-n]
    return y
def band_noise(t,seed,low,high):
    f=np.fft.rfftfreq(len(t),1/RATE)
    shape=1/(1+(low/np.maximum(f,.001))**10)/(1+(f/high)**10)
    a=np.random.default_rng(seed).normal(0,1,len(t))
    a=np.fft.irfft(np.fft.rfft(a)*shape,len(t))
    return a/max(float(np.std(a)),1e-12)
def mode(t,hz,decay,start=0,attack=.012):
    local=np.maximum(0,t-start)
    env=(1-np.exp(-local/attack))*np.exp(-local/decay);env[t<start]=0
    return np.sin(math.tau*hz*local)*env
def low_hit(t,start,gain=1):
    local=np.maximum(0,t-start)
    # A fast finite impact falls below the voiced register before the roar.
    phase=math.tau*(38*local+63*.027*(1-np.exp(-local/.027)))
    env=(1-np.exp(-local/.0025))*np.exp(-local/.34);env[t<start]=0
    return gain*(np.sin(phase)+.18*np.sin(phase*2.01))*env
def wav(path,x):
    with wave.open(str(path),'wb') as out:
        out.setnchannels(1);out.setsampwidth(2);out.setframerate(RATE)
        out.writeframes(np.round(x*32767).astype('<i2').tobytes())
def decode(path):
    r=subprocess.run([str(FF),'-v','error','-i',str(path),'-f','f32le','-ac','1','-ar',str(RATE),'pipe:1'],capture_output=True,check=True)
    return np.frombuffer(r.stdout,dtype='<f4').astype(np.float64)
def db(value):return 20*math.log10(max(float(value),1e-12))
def finish(name,x,recipe):
    t=np.arange(len(x))/RATE;x=np.asarray(x,dtype=np.float64)
    # Remove subsonic/DC drift and inaudible hiss before a single fixed gain.
    f=np.fft.rfftfreq(len(x),1/RATE)
    spectrum=np.fft.rfft(x);spectrum*=1/(1+(24/np.maximum(f,.001))**12)/(1+(f/5200)**12)
    x=np.fft.irfft(spectrum,len(x));x-=float(x.mean())
    x*=smooth(t/.004)*smooth((len(x)/RATE-t)/.11)
    x*=.80/max(float(abs(x).max()),1e-12)
    source=OUT/(name+'.wav');target=ASSETS/'sounds'/(name+'.ogg');wav(source,x)
    subprocess.run([str(FF),'-v','error','-i',str(source),'-c:a','libvorbis','-q:a','6','-y',str(target)],check=True)
    actual=decode(target);up=np.fft.irfft(np.fft.rfft(actual),len(actual)*4)*4
    f=np.fft.rfftfreq(len(actual),1/RATE);power=abs(np.fft.rfft(actual))**2;total=float(power.sum())
    record={'name':name,'revision':2,'origin':'Original deterministic mathematical synthesis; no imported recordings/samples/video audio.',
            'license':'Project license (see LICENSE)','cost':0,'duration_seconds':len(actual)/RATE,'sample_rate':RATE,'channels':1,
            'wav':str(source.relative_to(ROOT)),'wav_sha256':sha(source),'ogg':str(target.relative_to(ROOT)),'ogg_sha256':sha(target),
            'recipe':recipe,'encoded_peak_dbfs':round(db(abs(actual).max()),3),'true_peak_4x_dbfs':round(db(abs(up).max()),3),
            'rms_dbfs':round(db(np.sqrt(np.mean(actual*actual))),3),
            '24_160_hz_energy_fraction':round(float(power[(f>=24)&(f<160)].sum())/total,6),
            '160_2000_hz_energy_fraction':round(float(power[(f>=160)&(f<2000)].sum())/total,6),
            'above_4000_hz_energy_fraction':round(float(power[f>=4000].sum())/total,8),
            'clipped_sample_count':int(np.count_nonzero(abs(actual)>=.999)),'dc_offset':round(float(actual.mean()),9)}
    assert record['true_peak_4x_dbfs']<-.6 and record['clipped_sample_count']==0,record
    assert record['24_160_hz_energy_fraction']>.5 and record['above_4000_hz_energy_fraction']<.005,record
    return record,actual

unchanged_before={name:sha(ASSETS/'sounds'/name) for name in KEEP}
records=[];signals=[]

# Broad daylight mountain reveal: a heavy clean foundation enters first, then
# low stone resonators open out. No tinkling upward nursery-like arpeggio.
t=axis(3.85)
foundation=(np.sin(math.tau*65.4064*t)+.34*np.sin(math.tau*98.1096*t)+.18*np.sin(math.tau*130.8128*t))
awaken=foundation*.41*gate(t,.02,.38,1.16,2.05)
awaken+=low_hit(t,.025,.83)+low_hit(t,.64,.25)
for hz,gain,decay,start in [(146.83,.14,1.00,.10),(196.22,.095,1.35,.26),(261.63,.065,1.55,.26),(392.44,.028,1.15,.74)]:
    awaken+=mode(t,hz,decay,start,.032)*gain
# Just 18 ms of finite stone attack. There is no sustained noise bed.
awaken+=band_noise(t,27102721,180,1600)*.044*np.exp(-t/.018)
awaken+=delayed(awaken,.173)*.105+delayed(awaken,.327)*.045
r,x=finish('cangsheng_awaken',awaken,{'design':'Weighty, clean mountain emergence. Immediate 101→38-Hz finite body hit; stable C2/G2 foundation; low stone modes open into a quiet pearlescent tail.',
    'stable_modes_hz':[65.4064,98.1096,130.8128,146.83,196.22,261.63,392.44],
    'noise_seed':27102721,'noise_envelope':'18-ms exponential, attack only','changes_from_v1':'No high ascending arpeggio or continuous air bed.'})
records.append(r);signals.append(x)

# Huge steady chest roar, not speech, a frog croak, a comic pitch dive or a
# fast 5-Hz vibrato. The fundamental settles only 8 Hz across the whole call.
t=axis(3.25)
f0=56-8*smooth((t-.25)/2.55)
phase=math.tau*np.cumsum(f0)/RATE
voice=np.zeros_like(t)
for h in range(1,35):
    freq=f0*h
    weight=.36/(h**1.28)
    for centre,width,gain in [(148,83,.18),(285,100,.11),(510,170,.065),(820,250,.015)]:
        weight+=gain*np.exp(-.5*((freq-centre)/width)**2)/(h**.62)
    # Fixed glottal phases preserve a throat-like asymmetrical pulse.
    voice+=weight*np.sin(h*phase+.23*math.sin(h*1.71))
envelope=gate(t,.025,.065,.78,2.12)
roughness=.91+.055*np.sin(math.tau*21.2*t)+.025*np.sin(math.tau*32.7*t+.4)
roar=voice*roughness*envelope
roar+=np.sin(phase*.5)*.13*envelope
roar+=mode(t,111.4,.62,.035,.010)*.17+mode(t,165.1,.42,.045,.007)*.085
roar+=low_hit(t,.020,.48)
# Very quiet low throat excitation is gated inside the voiced body, never a
# long rushing/white-noise substitute for impact.
roar+=band_noise(t,27102722,85,920)*.011*gate(t,.04,.025,.60,.58)
roar+=delayed(roar,.119)*.083+delayed(roar,.271)*.035
r,x=finish('cangsheng_roar',roar,{'design':'Original low, stable, open-chest guardian roar with a finite body attack, asymmetrical harmonic vocal pulse and low stone resonance. No imitation of reference-video audio.',
    'fundamental_hz':'56→48 over 2.55 s; no periodic pitch vibrato','formant_centres_hz':[148,285,510,820],
    'noise_seed':27102722,'noise_envelope':'Quiet 85–920-Hz throat excitation, finite 1.2-s gated body',
    'changes_from_v1':'Removed 118→61-Hz diving vowel, 5.2-Hz pitch flutter and strong 1–2-kHz talking/croaking formants.',
    'biological_note':'Original fantasy guardian design, not a claim about real axolotl vocalization.'})
records.append(r);signals.append(x)

preview=np.zeros(round(9.0*RATE))
preview[:len(signals[0])]+=signals[0]
offset=round(4.5*RATE);preview[offset:offset+len(signals[1])]+=signals[1]
wav(OUT/'cangsheng-awaken-roar-v2-preview.wav',preview)
unchanged_after={name:sha(ASSETS/'sounds'/name) for name in KEEP}
assert unchanged_before==unchanged_after,'Unrelated cue was changed'
audit={'generator':str(Path(__file__).relative_to(ROOT)),'generator_sha256':sha(Path(__file__)),
       'created_date':'2026-10-27','revision':2,'source_recordings':[],'source_video_audio_used':False,'paid_assets':False,
       'production_format':'Mono Ogg Vorbis / 48000 Hz / q=6','cues':records,
       'unchanged_cues_before':unchanged_before,'unchanged_cues_after':unchanged_after,
       'prior_evidence':'verification/axolotl-20261027/audio/original-v1-history',
       'listening_preview':str((OUT/'cangsheng-awaken-roar-v2-preview.wav').relative_to(ROOT)),
       'limits':'Encoding/headroom/spectrum validated numerically. Subjective listening and actual game mix remain to be reviewed; no claim of listening approval.'}
(OUT/'audio-audit-v2.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
credits='''Cangsheng Axolotl audio — 2026-10-27

All four cues are original deterministic mathematical synthesis for this project.
No samples, recordings, reference-video audio or existing melody is imported.
No paid assets or third-party recording licenses are involved.
License: project LICENSE.

Current awaken / roar revision 2:
  tools/synth/original-cangsheng-audio-v2.py
  verification/axolotl-20261027/audio-v2/audio-audit-v2.json
  cangsheng_awaken.ogg, cangsheng_roar.ogg

Unchanged impact / flute revision 1:
  tools/synth/original-cangsheng-audio.py
  verification/axolotl-20261027/audio/audio-audit.json
  cangsheng_impact.ogg, cangsheng_flute.ogg

Old four-cue recordings, audit and generator remain in:
  verification/axolotl-20261027/audio/original-v1-history/
Fantasy guardian vocal design is not a claim about real axolotl sounds.
Numeric headroom/spectrum audit is not subjective listening approval.
'''
(ASSETS/'CANGSHENG_SOUND_CREDITS.txt').write_text(credits,encoding='utf-8')
print(json.dumps(audit,ensure_ascii=False,indent=2))
