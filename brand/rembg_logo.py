import sys
from PIL import Image
from rembg import remove, new_session
name = sys.argv[1]
session = new_session('u2net')
out = remove(Image.open(f'brand/logo-{name}-source.jpg').convert('RGB'), session=session)
out.save(f'brand/cut/logo_{name}_raw.png')
print(name, 'done', out.size)
