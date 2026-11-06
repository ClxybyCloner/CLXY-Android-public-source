"""Generate app and tray icons from the CLXY artwork in tool/clxy-icon.png.
Run from app/ with Pillow installed: python tool/app_icons.py
"""
from pathlib import Path
from PIL import Image, ImageDraw
import sys
HERE=Path(__file__).resolve().parent
APP=HERE.parent
ROOT=APP.parent
SOURCE=HERE/"clxy-icon.png"
ANDROID_SOURCE=APP/"assets/dashboard-logo.png"
SIZES=[16,20,24,32,40,48,64,128,256]
TRAY=[16,20,24,32,48,64]
GOOD=(63,225,176,255); BAD=(226,40,80,255); SET=(96,165,250,255)
def resize(img,n): return img.resize((n,n),Image.Resampling.LANCZOS)
def ico(path,img,sizes=SIZES):
 frames=[resize(img,n) for n in sizes]
 frames[-1].save(path,format="ICO",sizes=[(n,n) for n in sizes],append_images=frames[:-1])
def with_dot(img,color=None,muted=False):
 out=img.copy()
 if muted:
  muted_rgb=Image.blend(out.convert("RGB"),Image.new("RGB",out.size,(150,160,158)),.45).convert("RGBA")
  muted_rgb.putalpha(out.getchannel("A"))
  out=muted_rgb
 if color:
  n=out.width; d=ImageDraw.Draw(out); d.ellipse((n*.56,n*.56,n*.995,n*.995),fill=(0,0,0,255)); d.ellipse((n*.62,n*.62,n*.94,n*.94),fill=color)
 return out
def android_icons():
 mark=Image.open(ANDROID_SOURCE).convert("RGBA")
 bbox=mark.getchannel("A").getbbox()
 if bbox is None: raise SystemExit("CLXY mark is empty")
 mark=mark.crop(bbox)
 monochrome=Image.new("RGBA",mark.size,(255,255,255,0)); monochrome.putalpha(mark.getchannel("A"))
 navy=(5,11,22,255)
 def fit_inside(image,w,h):
  scale=min(w/image.width,h/image.height)
  return image.resize((max(1,round(image.width*scale)),max(1,round(image.height*scale))),Image.Resampling.LANCZOS)
 res=APP/"android/app/src/main/res"; density={"mdpi":1,"hdpi":1.5,"xhdpi":2,"xxhdpi":3,"xxxhdpi":4}
 for dpi,k in density.items():
  folder=res/f"mipmap-{dpi}"; folder.mkdir(parents=True,exist_ok=True)
  size=round(48*k); icon=Image.new("RGBA",(size,size),navy)
  fitted=fit_inside(mark,round(size*.78),round(size*.78))
  icon.alpha_composite(fitted,((size-fitted.width)//2,(size-fitted.height)//2))
  icon.save(folder/"ic_launcher.png",optimize=True)
  fg=round(108*k); safe=round(fg*.64)
  foreground=Image.new("RGBA",(fg,fg),(0,0,0,0))
  fitted=fit_inside(mark,safe,safe)
  foreground.alpha_composite(fitted,((fg-fitted.width)//2,(fg-fitted.height)//2))
  foreground.save(folder/"ic_launcher_foreground.png",optimize=True)
  background=Image.new("RGBA",(fg,fg),navy)
  background.save(folder/"ic_launcher_background.png",optimize=True)
  mono=Image.new("RGBA",(fg,fg),(0,0,0,0))
  fitted=fit_inside(monochrome,safe,safe)
  mono.alpha_composite(fitted,((fg-fitted.width)//2,(fg-fitted.height)//2))
  mono.save(folder/"ic_launcher_monochrome.png",optimize=True)
def main():
 image=Image.open(SOURCE).convert("RGBA")
 if image.width!=image.height: raise SystemExit("CLXY icon must be square")
 resize(image,256).save(APP/"assets/logo.png",optimize=True)
 ico(APP/"windows/runner/resources/app_icon.ico",image)
 ico(ROOT/"core/internal/desktop/appicon.ico",image)
 resize(image,192).save(ROOT/"core/internal/service/web/icon.png",optimize=True)
 linux=APP/"linux/runner/resources/app_icon.png"; linux.parent.mkdir(parents=True,exist_ok=True); resize(image,256).save(linux,optimize=True)
 mac=APP/"macos/Runner/Assets.xcassets/AppIcon.appiconset"
 for n in (16,32,64,128,256,512,1024): resize(image,n).save(mac/f"app_icon_{n}.png",optimize=True)
 android_icons()
 resize(image,256).save(ROOT/"docs/images/logo.png",optimize=True)
 for folder,states,png_size in ((APP/"assets/tray",{"off":None,"on":GOOD,"idle":GOOD,"error":BAD,"busy1":None,"busy2":None,"busy3":None,"busy4":None},64),(ROOT/"core/internal/desktop/tray",{"off":None,"waiting":None,"running":GOOD,"set":SET,"running-asleep":GOOD,"set-asleep":SET,"error":BAD},32)):
  folder.mkdir(parents=True,exist_ok=True)
  for old in folder.iterdir():
   if old.is_file(): old.unlink()
  for name,color in states.items():
   state=with_dot(image,color,name.endswith("asleep")); ico(folder/f"{name}.ico",state,TRAY); resize(state,png_size).save(folder/f"{name}.png",optimize=True)
if __name__=="__main__":
 if sys.argv[1:]==["--android-only"]: android_icons()
 else: main()
