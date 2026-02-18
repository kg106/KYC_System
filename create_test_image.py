from PIL import Image, ImageDraw
import random

# Create large image (2000x2000) to ensure OCR takes time
img = Image.new('RGB', (2000, 2000), color = (255, 255, 255))
d = ImageDraw.Draw(img)

# Add text to ensure OCR does something
d.text((100,100), "This is a dummy KYC document to test concurrency. " * 10, fill=(0,0,0))

# Add noise to slow down OCR analysis
for i in range(10000):
   x = random.randint(0, 2000)
   y = random.randint(0, 2000)
   d.point((x,y), fill=(0,0,0))

img.save('test_doc.png')
print("Created test_doc.png")
