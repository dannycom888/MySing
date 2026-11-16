#!/usr/bin/env python3
"""v1.0.42 re-verification boards: full frame (navy) + x4 crops at flag coords."""
import os
from PIL import Image, ImageDraw

ROOT = os.path.expanduser('~/workspace/shield-karaoke')
RES = os.path.join(ROOT, 'app/src/main/res/drawable-nodpi')
OUT = os.path.join(ROOT, 'dancers/audit142/boards')
BG = (26, 36, 54)

FLAGS = {
 'robot': {'38':[(215,150)],'47':[(200,140),(150,190)],'39':[(215,235)],'35':[(132,450)],
   '22':[(175,505),(255,510)],'17':[(145,300),(148,565)],'18':[(265,220),(270,310)],
   '37':[(100,135)],'31':[(292,335)],'32':[(60,232)],'44':[(415,200)],'34':[(342,425)],
   '49':[(35,200)],'21':[(292,447)],'08':[(73,580),(105,490)],'invite':[(50,393),(47,432)],
   '23':[(300,320),(350,440)],'07':[(300,435)],'33':[(65,427)],'45':[(40,207)],
   '06':[(200,140)],'12':[(205,100)],'14':[(200,105)],'10':[(150,135)]},
 'modern_woman': {'04':[(117,238)],'24':[(352,295),(95,300)],'27':[(225,277)],'31':[(258,393)],
   '33':[(312,245),(300,268)],'37':[(283,65)],'39':[(70,330)],'41':[(310,271)],'42':[(125,276)],
   '50':[(125,185)],'still':[(190,252)],'invite':[(192,257)]},
 'ancient_girl': {'46':[(205,360)],'51':[(106,496)],'invite':[(5,442)]},
 'ancient_woman': {'05':[(20,250)],'13':[(295,250)],'47':[(295,130)],'52':[(275,210),(279,280)],
   '51':[(62,208)],'61':[(10,140),(120,130)],'44':[(275,309)],'46':[(290,443)],'56':[(297,508)],
   'invite':[(175,395),(215,270)],'03':[(91,385)],'29':[(236,443)],'36':[(244,301),(265,360)],
   '53':[(35,195)],'74':[(265,205)],'90':[(270,170)],'still':[(238,320)]},
 'sumo': {'31':[(365,225)],'41':[(350,320)],'42':[(325,290)],'48':[(320,280)],'54':[(140,430)],
   '58':[(330,210)],'59':[(125,235)],'27':[(355,430)],'50':[(35,290)],'53':[(335,410)],
   '56':[(335,235)],'30':[(75,320)]},
 'baby': {'28':[(198,499)],'invite':[(123,569)]},
}

def frame_path(key, tag):
    if tag == 'still':
        return os.path.join(RES, f'dancer_{key}.png')
    if tag == 'invite':
        return os.path.join(RES, f'dancer_{key}_invite.webp')
    return os.path.join(RES, f'dancer_{key}_{tag}.webp')

for key, frames in FLAGS.items():
    for tag, coords in frames.items():
        im = Image.open(frame_path(key, tag)).convert('RGBA')
        W, H = im.size
        full = Image.new('RGB', (W, H), BG)
        full.paste(im, (0, 0), im)
        S = 150
        crops = []
        for (x, y) in coords:
            x0 = min(max(0, x - S // 2), max(0, W - S)); y0 = min(max(0, y - S // 2), max(0, H - S))
            c = Image.new('RGB', (S, S), BG)
            cc = im.crop((x0, y0, x0 + S, y0 + S))
            c.paste(cc, (0, 0), cc)
            c = c.resize((S * 3, S * 3), Image.LANCZOS)
            d = ImageDraw.Draw(c)
            d.rectangle([0, 0, 130, 22], fill=(0, 0, 0))
            d.text((4, 5), f'{key} {tag} @({x},{y})', fill=(255, 255, 0))
            crops.append(c)
        cw = S * 3
        board = Image.new('RGB', (W + cw + 16, max(H, len(crops) * (cw + 8))), (10, 10, 10))
        board.paste(full, (0, 0))
        yy = 0
        for c in crops:
            board.paste(c, (W + 16, yy)); yy += cw + 8
        board.save(os.path.join(OUT, f'{key}_{tag}.png'))
print('boards done:', sum(len(v) for v in FLAGS.values()))
