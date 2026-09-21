from PIL import Image, ImageDraw, ImageFont
from pathlib import Path

root=Path(__file__).resolve().parents[1]
out=root/'lab12-case-app'/'screenshots'/'main-form.png'
out.parent.mkdir(parents=True,exist_ok=True)
img=Image.new('RGB',(1400,820),'#f3f4f6'); d=ImageDraw.Draw(img)
try:
 f=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',22); fb=ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf',25); fs=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',18)
except: f=fb=fs=None
d.rectangle((0,0,1400,70),fill='#1f4e78'); d.text((28,18),'IntelligentTestAgent — CASE-приложение',font=fb,fill='white')
d.rectangle((25,95,360,740),fill='white',outline='#9ca3af',width=2); d.text((45,115),'Проекты анализа',font=fb,fill='#1f2937')
projects=['Order Processor [JAVA]','Calculator [JAVA]']; y=170
for i,p in enumerate(projects):
 color='#dbeafe' if i==0 else 'white'; d.rectangle((40,y-8,345,y+38),fill=color,outline='#d1d5db'); d.text((52,y),p,font=f,fill='#111827'); y+=60
d.rounded_rectangle((65,670,320,720),8,fill='#2563eb'); d.text((103,684),'Добавить проект',font=f,fill='white')
d.rectangle((385,95,1375,740),fill='white',outline='#9ca3af',width=2)
d.rectangle((385,95,800,145),fill='#dbeafe'); d.text((500,109),'Запуски анализа',font=fb,fill='#1f2937'); d.text((950,109),'Тест-кейсы',font=fb,fill='#4b5563')
headers=['ID','Статус','Начало','LLM-модель']; xs=[420,510,700,1030]
for x,h in zip(xs,headers): d.text((x,175),h,font=fb,fill='#374151')
d.line((410,210,1345,210),fill='#9ca3af',width=2)
rows=[['1','COMPLETED','2026-07-01 10:00','openai/gpt-oss-20b:free'],['2','COMPLETED','2026-07-02 11:30','—']]
y=230
for row in rows:
 for x,t in zip(xs,row): d.text((x,y),t,font=f,fill='#111827')
 d.line((410,y+40,1345,y+40),fill='#e5e7eb'); y+=65
d.rounded_rectangle((910,665,1125,715),8,fill='#059669'); d.text((935,678),'Добавить тест-кейс',font=fs,fill='white')
d.rounded_rectangle((1140,665,1320,715),8,fill='#6b7280'); d.text((1185,678),'Обновить',font=f,fill='white')
d.text((30,775),'Представление экранной формы, сформированное программно по реальному Swing-интерфейсу.',font=fs,fill='#4b5563')
img.save(out)
print(out)
