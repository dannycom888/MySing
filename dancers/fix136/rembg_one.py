import sys, glob
from PIL import Image
from rembg import remove, new_session
n = int(sys.argv[1])
src = glob.glob(f'dancers/fix136/gen/media-generation-regen-{n}-try1-*.webp')[0]
session = new_session('u2net')
out = remove(Image.open(src).convert('RGB'), session=session)
out.save(f'dancers/fix136/cut/{n:02d}_raw.png')
print(n, 'done', out.size)
