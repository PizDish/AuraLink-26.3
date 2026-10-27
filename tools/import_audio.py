#!/usr/bin/env python3
"""Convert files you own to PCM WAV for AuraLink (local-only; no streaming/downloading)."""
import argparse
from pathlib import Path
import shutil
import subprocess
import sys

SUPPORTED={'.mp3','.m4a','.aac','.ogg','.flac','.opus','.wav','.aiff'}

def main():
    parser=argparse.ArgumentParser(description='Convert local audio tracks for AuraLink via ffmpeg')
    parser.add_argument('files',nargs='+',type=Path,help='Local files you have permission to use')
    parser.add_argument('--game-dir',type=Path,required=True,help='Minecraft instance game directory')
    args=parser.parse_args()
    ffmpeg=shutil.which('ffmpeg')
    if not ffmpeg: raise SystemExit('FFmpeg not installed in PATH. Obtain it from its official project.')
    dest=args.game_dir/'auralink'/'tracks'
    dest.mkdir(parents=True,exist_ok=True)
    results=[]
    for source in args.files:
        if not source.is_file() or source.suffix.lower() not in SUPPORTED:
            print('SKIP unsupported/missing:',source,file=sys.stderr);continue
        safe=''.join(c if c.isalnum() or c in ' -_()' else '_' for c in source.stem).strip()[:120] or 'audio'
        output=dest/(safe+'.wav')
        if output.exists():
            print('SKIP already exists:',output,file=sys.stderr);continue
        command=[ffmpeg,'-hide_banner','-loglevel','error','-nostdin','-i',str(source),
                 '-vn','-ac','2','-ar','44100','-c:a','pcm_s16le','-f','wav',str(output)]
        proc=subprocess.run(command,check=False)
        if proc.returncode != 0:
            output.unlink(missing_ok=True)
            print('FAILED:',source,file=sys.stderr)
        else:
            print('Created:',output);results.append(output)
    print('Converted:',len(results),'file(s). Note: the mod limits each WAV file to 60 MB.')
if __name__=='__main__':main()
