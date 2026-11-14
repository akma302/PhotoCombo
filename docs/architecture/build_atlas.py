"""Rebuild the source-backed SVG architecture atlas with Python's standard library."""
from pathlib import Path
from html import escape as esc
from urllib.parse import quote
import hashlib
import json
import re
import subprocess
import textwrap

OUT = Path(__file__).resolve().parent
ROOT = OUT.parent.parent
REV = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
STAMP = '2026-11-14'
MODULES = re.findall(r'include\("(:[^\"]+)"\)', (ROOT / 'settings.gradle.kts').read_text('utf-8'))
FILES = []
TESTS = []
for module in MODULES:
    folder = ROOT / module[1:].replace(':', '/')
    for path in sorted((folder / 'src').rglob('*.kt')):
        rel = path.relative_to(ROOT).as_posix()
        if '/desktopTest/' in rel or '/commonTest/' in rel:
            TESTS.append(rel)
            continue
        if not any('/' + ss + '/' in rel for ss in ('commonMain', 'desktopMain')):
            continue
        body = path.read_text('utf-8')
        declarations = []
        # Index declarations at column zero; this is intentionally not a Kotlin AST.
        for number, line in enumerate(body.splitlines(), 1):
            if re.match(r'^(?:(?:public|private|internal|protected|data|sealed|enum|abstract|open|expect|actual|suspend|inline|fun|const)\s+)*(?:class|interface|object|fun|val|var|typealias)\s+', line):
                declarations.append({'line': number, 'text': line, 'visibility': next((v for v in ['private', 'internal', 'protected'] if re.search(r'\b' + v + r'\b', line.split('(')[0])), 'public')})
        FILES.append({'module': module, 'path': rel, 'name': path.name,
                      'sourceSet': 'desktopMain' if '/desktopMain/' in rel else 'commonMain',
                      'package': re.search(r'^package (.+)$', body, re.M).group(1),
                      'declarations': declarations,
                      'imports': re.findall(r'^import (com\.combo\..+)$', body, re.M),
                      'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})

COL = {'app': '#c2a7ff', 'feature': '#82b7ff', 'domain': '#57ddc4', 'data': '#f0c175',
       'core': '#b4d882', 'ui': '#ec9fc9', 'test': '#a7b8ce', 'risk': '#ffaaa0', 'ink': '#eef3fb', 'muted': '#a4b4cc'}
W, H = 1920, 1380
PAGES = []

def source(name):
    matches = [f['path'] for f in FILES if f['name'] == name or f['path'] == name]
    if len(matches) != 1:
        if (ROOT / name).exists():
            return name
        raise ValueError((name, matches))
    return matches[0]

def href(name):
    return '../../' + quote(source(name), safe='/')

def wrap(value, width=64):
    return textwrap.wrap(value, width=width, break_long_words=False, break_on_hyphens=False) or ['']

CSS = '''
text{font-family:"Segoe UI",Arial,sans-serif;fill:#eef3fb}
.title{font-size:44px;font-weight:650;letter-spacing:-1px}
.subtitle{font-size:22px;fill:#a4b4cc}
.label{font-size:16px;letter-spacing:2px;font-weight:650}
.body{font-size:21px}.small{font-size:18px;fill:#a4b4cc}
.mono{font-family:Consolas,"Courier New",monospace;font-size:18px}
.edge{fill:none;stroke-width:2.6;stroke-linejoin:round;stroke-linecap:round}
.flow{stroke-dasharray:10 7;animation:flow 16s linear infinite}
.card:hover .card-bg{stroke:#eff7ff;filter:brightness(1.08)}
a:focus .card-bg{stroke:#fff;stroke-width:3}
@keyframes flow{to{stroke-dashoffset:-204}}
@media(prefers-reduced-motion:reduce){.flow{animation:none}}
@media print{.flow{animation:none}}
'''

def defs():
    result = '<defs><linearGradient id="paper" x2="1" y2="1"><stop stop-color="#111e33"/><stop offset="1" stop-color="#091323"/></linearGradient>'
    result += '<pattern id="grid" width="40" height="40" patternUnits="userSpaceOnUse"><circle cx="1" cy="1" r="1" fill="#29405b" opacity=".45"/></pattern>'
    for key, color in COL.items():
        result += f'<marker id="arrow-{key}" markerWidth="10" markerHeight="10" refX="8" refY="5" orient="auto" markerUnits="userSpaceOnUse"><path d="M1 1 L8 5 L1 9" fill="none" stroke="{color}" stroke-width="2"/></marker>'
        result += f'<marker id="inherit-{key}" markerWidth="14" markerHeight="14" refX="12" refY="7" orient="auto" markerUnits="userSpaceOnUse"><path d="M1 1 L12 7 L1 13 Z" fill="#101e32" stroke="{color}" stroke-width="1.8"/></marker>'
    return result + '</defs>'

class Page:
    def __init__(self, slug, title, subtitle, color='app'):
        self.slug, self.title, self.subtitle, self.color = slug, title, subtitle, color
        self.s = []
        self.rect(0, 0, W, H, '#0b1526', rx=0)
        self.s.append(f'<rect width="{W}" height="{H}" fill="url(#paper)"/><rect width="{W}" height="{H}" fill="url(#grid)"/>')
        self.rect(64, 60, 6, 94, COL[color], rx=3)
        self.text(90, 78, 'PHOTOCOMBO / ARCHITECTURE ATLAS', 'label', color)
        self.text(88, 132, title, 'title')
        self.text(90, 173, subtitle, 'subtitle')
        self.line(64, 207, 1856, 207, 'test', opacity='.35')
        self.line(64, H-66, 1856, H-66, 'test', opacity='.35')
        self.text(64, H-31, f'{STAMP}  •  source {REV[:12]}  •  commonMain + desktopMain', 'small')
        self.s.append(f'<a href="00-guide.svg"><text x="1530" y="{H-31}" class="small">← Навигация по атласу</text></a>')
        PAGES.append(self)

    def rect(self, x,y,w,h,fill='#122239',stroke=None,rx=18,extra=''):
        self.s.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{rx}" fill="{fill}"'+(f' stroke="{stroke}"' if stroke else '')+f' {extra}/>')

    def text(self,x,y,t,cls='body',color=None,size=None):
        style = (f'fill:{COL.get(color,color)};' if color else '') + (f'font-size:{size}px;' if size else '')
        self.s.append(f'<text x="{x}" y="{y}" class="{cls}" style="{style}">{esc(str(t))}</text>')

    def lines(self,x,y,values,width=60,cls='body',color=None,step=29):
        for value in values:
            for line in wrap(value,width):
                self.text(x,y,line,cls,color)
                y += step
        return y

    def line(self,x1,y1,x2,y2,color='domain',opacity='1'):
        self.s.append(f'<path d="M{x1} {y1} L{x2} {y2}" stroke="{COL[color]}" stroke-width="1.5" opacity="{opacity}"/>')

    def arrow(self,points,label='',color='domain',kind='call',label_pos=None):
        path = 'M' + ' L'.join(f'{x} {y}' for x,y in points)
        dash = ' stroke-dasharray="7 6"' if kind in ('impl','own') else ''
        marker = 'inherit' if kind == 'impl' else 'arrow'
        self.s.append(f'<path d="{path}" class="edge'+(' flow' if kind=='flow' else '')+f'" stroke="{COL[color]}"{dash} marker-end="url(#{marker}-{color})"/>')
        if label:
            x,y = label_pos or ((points[0][0]+points[-1][0])/2, (points[0][1]+points[-1][1])/2-13)
            width = max(90,len(label)*10.2+22)
            self.rect(x-width/2,y-20,width,29,'#0c1729',rx=6)
            self.text(x-width/2+11,y,label,'small',color)

    def card(self,x,y,w,h,title,tag,lines,color='domain',src=None,kind='class'):
        link = href(src) if src else None
        if link: self.s.append(f'<a href="{link}" target="_blank">')
        self.s.append(f'<g class="card"><title>{esc(title + " — " + tag + (" | " + source(src) if src else ""))}</title>')
        self.rect(x,y,w,h,'#122239',COL[color],extra='class="card-bg" stroke-opacity=".65"'+(' stroke-dasharray="7 5"' if kind=='interface' else ''))
        self.rect(x,y+19,4,h-38,COL[color],rx=2)
        self.text(x+22,y+32,tag,'label',color)
        self.text(x+22,y+69,title,'body',None,size=24 if len(title)<34 else 21)
        self.lines(x+22,y+103,lines,width=int((w-44)/10.7),step=29)
        if src: self.text(x+w-29,y+32,'↗','small',color)
        self.s.append('</g>')
        if link: self.s.append('</a>')

    def note(self,x,y,w,title,lines,color='risk',h=135):
        self.s.append('<g class="note">')
        self.rect(x,y,w,h,'#142238',COL[color],rx=14,extra='class="note-bg" stroke-opacity=".35"')
        self.text(x+20,y+33,title,'body',color)
        self.lines(x+20,y+65,lines,int((w-40)/10.5),'small',step=26)
        self.s.append('</g>')

    def render(self):
        return ''.join(self.s)

def write(path, text):
    path.write_text(text, encoding='utf-8', newline='\n')

def svg_doc(body, height, title):
    return f'''<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="1920" height="{height}" viewBox="0 0 1920 {height}" role="img" aria-labelledby="svg-title svg-desc">
<title id="svg-title">{esc(title)}</title><desc id="svg-desc">Архитектура PhotoCombo по исходникам {REV}. Цвет — роль, штриховой контур — интерфейс, пунктирная стрелка с треугольником — реализация. Анимация линий обозначает направление данных, а не измеренную активность.</desc>
<style>{CSS}</style>{defs()}{body}</svg>'''

def make_pages():
    p = Page('00-guide', 'Архитектура, которую можно изучать', 'От Gradle-графа до одного пикселя: роли, контракты, владение, данные и ограничения текущей реализации.')
    p.text(64,266,'11',size=72); p.text(170,256,'модулей Gradle','body'); p.text(170,288,'10 production + test-support','small')
    p.text(675,266,str(len(FILES)),size=72); p.text(825,256,'Kotlin-файлов Main','body'); p.text(825,288,'включая тестовые адаптеры','small')
    p.text(1290,266,str(len(TESTS)),size=72); p.text(1435,256,'файлов тестов','body'); p.text(1435,288,'каталог, не результат запуска','small')
    nav = [
        ('01-modules','01 / Границы модулей','Полный граф api / implementation','app'),
        ('02-contracts','02 / Контракты и DI','Кто создаёт и что подменяется','domain'),
        ('03-navigation','03 / Экраны и навигация','Четыре фичи, два типа владельцев','feature'),
        ('04-builder','04 / Внутри Builder','Событие → мутация → снимок → Undo','feature'),
        ('05-images','05 / Изображения и память','Три маршрута загрузки и два кэша','domain'),
        ('06-storage','06 / Импорт и хранение','Файлы, SQLite, JSON и целостность','data'),
        ('07-render-export','07 / Рендер и экспорт','Общая геометрия и сохранённый PNG','core'),
        ('08-reuse','08 / Повторное использование','UI, дизайн-система и тестовые швы','ui'),
        ('09-lifetimes','09 / Время жизни','Владельцы, отмена и зоны внимания','risk'),
    ]
    for i,(slug,title,body,color) in enumerate(nav):
        x=64+(i%3)*610; y=345+(i//3)*180
        p.s.append(f'<a href="{slug}.svg">')
        p.card(x,y,572,144,title,'ОТКРЫТЬ ГЛАВУ →',[body],color)
        p.s.append('</a>')
    p.text(64,957,'Условные обозначения','body')
    for i,(color,label) in enumerate([('app','Сборка'),('feature','Экраны'),('domain','Сервисы'),('data','Хранение'),('core','Геометрия'),('ui','Общий UI')]):
        x=64+i*298; p.rect(x,985,12,12,COL[color],rx=6); p.text(x+24,998,label,'small')
    p.arrow([(68,1045),(188,1045)],'','test'); p.text(210,1052,'использует / вызывает → адресат','small')
    p.arrow([(690,1045),(810,1045)],'','domain','impl'); p.text(832,1052,'реализация → контракт','small')
    p.arrow([(1300,1045),(1420,1045)],'','data','flow'); p.text(1442,1052,'данные → получатель','small')
    p.note(64,1094,872,'public / internal / private ≠ время жизни',[
        'Модификаторы описывают доступ. SINGLE / ROOT / ENTRY / LOCAL — владельца.',
        'Пунктирный контур: interface. Цвет показывает роль, а не видимость.'
    ],'app',155)
    p.note(984,1094,872,'Как читать и проверять',[
        'Начните с 01 → 03 → 04 → 07. Карточки с ↗ ведут к исходникам.',
        'ONBOARDING.md — объяснения; SOURCE_INDEX.md — все Main-файлы и тесты.',
        'Анимация иллюстративная. Статический кадр содержит всю информацию.'
    ],'core',155)

    p = Page('01-modules','01 / Реальные границы модулей','Стрелка A → B: A зависит от B. Слева — скелет; справа — все прямые production-зависимости.','app')
    p.card(64,246,620,160,':composeApp','COMPOSITION ROOT • desktop entry',[
        'main → Koin → Window → PcDesktopTheme → AppNav',
        'Точка сборки всех четырёх независимых фич.'
    ],'app','main.kt')
    p.arrow([(374,406),(374,455)],color='app')
    p.card(64,455,620,185,':feature:* × 4','LIBRARY / COMBO-LIST / VIEWER / BUILDER',[
        'Screen + ViewModel; переходы через callbacks.',
        'Каждая: api(core, data, domain);',
        'implementation(design-system, ui-common).'
    ],'feature','feature/builder/build.gradle.kts')
    p.arrow([(210,640),(210,700)],color='feature')
    p.arrow([(540,640),(540,700)],color='ui')
    p.card(64,700,295,178,':domain','APPLICATION SERVICES',[
        'api(core, data)', 'Репозитории + кэши.'
    ],'domain','domain/build.gradle.kts')
    p.card(389,700,295,178,':ui-common','SHARED COMPOSE',[
        'api(data, DS)', 'impl(core, domain)'
    ],'ui','ui-common/build.gradle.kts')
    p.arrow([(210,878),(210,924)],color='data')
    p.card(64,924,295,146,':data','ROOM + FILES',['api(core)'],'data','data/build.gradle.kts')
    p.card(389,924,295,146,':design-system','UI LIBRARY',['Нет project-зависимостей.'],'ui','design-system/build.gradle.kts')
    p.arrow([(540,878),(540,918)],color='ui')
    p.arrow([(210,1070),(210,1111)],color='core')
    p.card(64,1111,620,159,':core','RENDER + MODEL + PLATFORM PORTS',[
        'Нет project-зависимостей; есть Compose / Okio.',
        'commonMain + desktopMain; не «чистый Kotlin без UI».'
    ],'core','core/build.gradle.kts')
    cols=['App','Lib','List','View','Build','UI','DS','Dom','Data','Core','Test']
    mods=[':composeApp',':feature:library',':feature:combo-list',':feature:viewer',':feature:builder',':ui-common',':design-system',':domain',':data',':core',':test-support']
    p.text(745,258,'A = api   •   I = implementation   •   · = нет прямого ребра','small')
    x0,y0,cell=965,310,78
    for c,col in enumerate(cols): p.text(x0+c*cell+10,y0,col,'small')
    for r,mod in enumerate(mods):
        yy=y0+34+r*59
        p.text(745,yy+27,mod.replace(':feature:',':f:'),'mono')
        build=(ROOT/mod[1:].replace(':','/')/'build.gradle.kts').read_text('utf-8')
        # All project statements before desktopTest are production declarations.
        production=build.split('desktopTest')[0]
        edges={m:kind for kind,m in re.findall(r'(api|implementation)\(project\("([^"]+)"\)\)',production)}
        for c,target in enumerate(mods):
            value='—' if target==mod else edges.get(target,'')[:1].upper() or '·'
            color='domain' if value=='A' else ('feature' if value=='I' else 'muted')
            p.rect(x0+c*cell,yy,cell-4,51,'#152a3d' if value in ('A','I') else '#101d30',rx=8)
            p.text(x0+c*cell+27,yy+33,value,'body',color)
    p.note(745,1045,1111,'Что нельзя вывести из упрощённой лестницы',[
        'Domain напрямую зависит от data и публикует Room-сущности в API; фичи знают data.',
        'api раскрывает зависимость потребителям; implementation не делает классы private.',
        'Все цели сейчас jvm("desktop"). Наличие commonMain само по себе не даёт Android-версию.'
    ],'app',144)
    p.text(745,1234,'test-support: api на пять нижних модулей; подключается только из desktopTest.','small')
    p.text(745,1268,'Охрана: verifyModuleGraph + ArchitectureRulesTest (границы импортов и циклы).','small')

    p = Page('02-contracts','02 / Абстракции, реализации и DI','Пунктир с пустым треугольником направлен от реализации к контракту. Все именованные типы ниже public.','domain')
    rows=[
        ('PhotoRepository','PhotoRepositoryImpl','all / get / importPhotos / delete / thumbPath','Repositories.kt','Impls.kt','domain'),
        ('ComboRepository','ComboRepositoryImpl','all / get / saveCombo / delete • транзакция','Repositories.kt','Impls.kt','domain'),
        ('BitmapSource','LruBitmapSource','get(photoId, requiredPx) • общий декод','Repositories.kt','LruBitmapSource.kt','domain'),
        ('GallerySettingsRepository','GallerySettingsRepositoryImpl','load / saveScale • Mutex + memory','GallerySettingsRepository.kt','GallerySettingsRepository.kt','domain'),
        ('GallerySettingsStorage','FileGallerySettingsStorage','read / write • JSON + временный файл','GallerySettingsStorage.kt','GallerySettingsStorage.kt','data'),
        ('ImageCodec','SkiaImageCodec','readBounds / readOrientation / decode / encode','ImageCodec.kt','SkiaImageCodec.kt','core'),
        ('ImagePicker / ImageSource','DesktopImagePicker / FileImageSource','pickImages / openSource • native file dialog','ImagePicker.kt','DesktopImagePicker.kt','core'),
        ('PngOutput','DesktopPngOutput','chooseDestination / write • PNG на диск','PngOutput.kt','DesktopPngOutput.kt','core'),
    ]
    for i,(contract,impl,desc,cs,isrc,color) in enumerate(rows):
        y=240+i*115
        p.card(64,y,555,96,contract,'INTERFACE',[],color,src=cs,kind='interface')
        p.arrow([(687,y+49),(624,y+49)],color=color,kind='impl')
        p.s.append(f'<a href="{href(isrc)}" target="_blank"><title>{esc(source(isrc))}</title>')
        p.rect(700,y,580,96,'#122239',COL[color],rx=14)
        p.text(722,y+34,impl,'body',color,size=21)
        p.text(722,y+70,desc,'small')
        p.s.append('</a>')
    p.note(1320,240,536,'Koin: appModule',[
        'single: PhotoStorage, PhotoImporter,',
        'PhotoRepository, ComboRepository,',
        'GallerySettingsStorage / Repository,',
        'ComboPngExporter.'
    ],'domain',191)
    p.note(1320,451,536,'Koin: desktopModule()',[
        'single: FileSystem.SYSTEM, AppDirs,',
        'Logger, ImageCodec, ImagePicker,',
        'PngOutput, CoroutineDispatcher,',
        'WallClock, IdGenerator, AppDb,',
        'PhotoDao, ComboDao, BitmapSource.'
    ],'core',217)
    p.note(1320,688,536,'Koin: viewModelModule',[
        'Четыре viewModel-фабрики.',
        'Экземпляр кэшируется во владельце',
        'ViewModelStore, а не как single.',
        'Builder получает EntryArgs(handle).',
        'PhotoAssets создаётся вручную в VM.'
    ],'feature',217)
    p.note(1320,925,536,'Другие реализации',[
        'AppDirs ← DesktopAppDirs',
        'Logger ← DesktopLogger',
        'WallClock / IdGenerator ← SAM-лямбды',
        'BackgroundStrategy ←',
        'AverageAreaOklabStrategy (object).'
    ],'core',217)
    p.note(64,1180,1792,'Генерируемые и локальные объекты',[
        'Room генерирует реализации PhotoDao / ComboDao / AppDb и actual AppDbConstructor. Это не ручные классы репозитория.',
        'BackgroundResolver.Default — companion val; стратегия — Kotlin object. FileImageSource — на выбранный файл, не Koin single.'
    ],'data',106)

    p = Page('03-navigation','03 / Навигация и четыре разных экрана','Features не импортируют друг друга: AppNav соединяет callbacks и типизированные ScreenKey.','feature')
    p.card(64,240,1792,159,'AppNav → NavDisplay + mutableStateListOf<NavKey>','APP • PUBLIC COMPOSABLE',[
        'Library / Combos → общий rootOwner. Viewer / Builder → private EntryScope с отдельным owner.',
        'Стек в remember; сериализации и восстановления после перезапуска нет. switchTab меняет один стек.'
    ],'app','AppNav.kt')
    screens=[
        ('LibraryScreen','LibraryViewModel','ROOT',['PhotoRepository + ImagePicker','GalleryScaleState(Photos)','StateFlow: photos, selection','Channel → messages','Выбор упорядочен; max 10 фото.'],'LibraryViewModel.kt'),
        ('ComboListScreen','ComboListViewModel','ROOT',['ComboRepository + PhotoRepository','PhotoAssets + GalleryScaleState','State: combos / photos / images','exportPng: suspend (String) → String?','Один экспорт за раз.'],'ComboListViewModel.kt'),
        ('PhotoViewerScreen','PhotoViewerViewModel','ENTRY',['PhotoRepository + BitmapSource','PhotoAssets: окно index ± 1','State: photos / images','UI: pager, scale, offset в remember','Viewer photoId передаётся экрану.'],'PhotoViewerViewModel.kt'),
        ('ComboBuilderScreen','ComboBuilderViewModel','ENTRY',['ComboRepository + PhotoRepository','PhotoAssets + DraftHistory','State: BuilderUiState + bitmaps','Channel: saved / messages','Аргументы: comboId или photos.'],'ComboBuilderViewModel.kt'),
    ]
    for i,(screen,vm,scope,lines,src) in enumerate(screens):
        x=64+i*460
        p.arrow([(x+206,399),(x+206,455)],color='app',kind='own')
        p.card(x,455,412,353,screen,'PUBLIC • '+scope,[vm]+lines,'feature',src)
    p.text(64,864,'Переходы через AppNav','body','app')
    p.arrow([(80,902),(620,902)],'Library → Viewer(photoId)','feature',label_pos=(350,889))
    p.arrow([(990,902),(1840,902)],'Library → Builder(photos)','feature',label_pos=(1410,889))
    p.arrow([(80,974),(620,974)],'Combos → Builder(comboId)','feature',label_pos=(350,961))
    p.arrow([(990,974),(1840,974)],'Combos → Builder() / новое','feature',label_pos=(1410,961))
    p.note(64,1025,872,'Back: последний зарегистрированный обработчик',[
        'Window Esc / BackKeyHost → BackDispatcher.dispatchBack().',
        'RegisterBackHandler: DisposableEffect + rememberUpdatedState.',
        'Диалог / режим выбора / Builder перехватывают back; иначе pop стека.',
        'Кнопки экрана также вызывают onBack. Регистрация снимается onDispose.'
    ],'ui',220)
    p.note(984,1025,872,'Публичный StateFlow не означает глубокую неизменяемость',[
        'Library хранит выбор; ComboList — список и статус экспорта.',
        'Viewer держит pager/zoom в UI; Builder — viewport в ViewModel.',
        'BuilderUiState.items содержит mutable ItemDraft; revision сигналит',
        'об изменении. Новый copy() состояния не копирует сами драфты.'
    ],'risk',220)

    p = Page('04-builder','04 / Редактор: события, мутации и снимки','Три разных формы данных: mutable ItemDraft → immutable-by-convention ItemSnap / ComboSnap → Room entities.','feature')
    p.card(64,240,540,217,'Ввод и панели','UI • В ОСНОВНОМ INTERNAL',[
        'editorGestures / classifyGesture / nearEdge',
        'editorWheelZoom / BuilderInspector',
        'BuilderTopBar / BuilderCanvasToolbar',
        'BuilderViewControls / диалоги'
    ],'ui','EditorGestures.kt')
    p.card(690,240,540,217,'BuilderEvent → onEvent()','PUBLIC • VIEWMODEL',[
        'ItemPressed / SnapDrag / PointerEditFinished',
        'ContentPan / ContentZoom / BorderResize',
        'AddItems / ReplacePhoto / SetAspect',
        'Undo / Redo / Save / ViewportZoom'
    ],'feature','BuilderContract.kt')
    p.card(1316,240,540,217,'BuilderUiState','PUBLIC • ENTRY STATE',[
        'items + photos + selectedItemId',
        'canvasW/H + background + name',
        'viewport + revision + isDirty',
        'canUndo/Redo + saving + loaded'
    ],'feature','BuilderContract.kt')
    p.arrow([(604,348),(684,348)],color='feature')
    p.arrow([(1230,348),(1310,348)],color='feature',kind='flow')
    p.arrow([(870,457),(870,510)],color='core')
    p.card(64,510,540,275,'DraftHistory','INTERNAL CLASS • OWNED BY VM',[
        'begin(): снимок до мутации',
        'end(): сравнение → один checkpoint',
        'cancel(): restore(pending), без шага',
        'checkpoint(): кнопочные операции',
        'UndoStack<ComboSnap>: 50 шагов',
        'snapshot / restore переданы лямбдами'
    ],'feature','DraftHistory.kt')
    p.card(690,510,540,275,'DraftEditor + core rules','INTERNAL OBJECT + PUBLIC CORE',[
        'moveToFront / deleteItem / addItems',
        'replacePhoto / applyPreset',
        'applyFramingMode / newComboDrafts',
        'ItemGestures: движение / resize / zoom',
        'FrameSnap: MoveFrame / PanPhoto / Resize',
        'relayout: изменение формата холста'
    ],'core','DraftEditor.kt')
    p.card(1316,510,540,275,'ItemDraft','PUBLIC CLASS • MUTABLE',[
        'id: val; геометрия и фото: var',
        'framingMode, contentScale, panX/Y',
        'VM меняет драфт → emitRevision()',
        'UI наблюдает state + bitmaps',
        'frameFor() → buildItemFrame()',
        'EditorCanvas вызывает drawCombo()'
    ],'core','Drafts.kt')
    p.arrow([(690,635),(610,635)],'checkpoint','feature',label_pos=(648,613))
    p.arrow([(1230,635),(1310,635)],color='core')
    p.card(64,850,540,217,'ComboSnap + ItemSnap','INTERNAL DATA CLASSES',[
        'Копия каждого поля рамки.',
        'Размеры холста + фон + список снимков.',
        'Имя, viewport, выделение — вне Undo.',
        'Равенство снимков фильтрует пустой жест.'
    ],'data','ComboSnap.kt')
    p.card(690,850,540,217,'Сохранение отдельной копии','PRIVATE save() • IO AFTER SNAPSHOT',[
        'snapshot().trimmedToPhotos(photos)',
        'toDraft() → toEntities(comboId)',
        'ComboRepository.saveCombo()',
        'saved → экран вызывает onBack()'
    ],'data','FinalizeCanvas.kt')
    p.card(1316,850,540,217,'Viewport и snap-сессия','LOCAL EDITOR CONCERNS',[
        'Viewport.zoomedAround() → новый value.',
        'Pan / zoom вида не меняют Undo.',
        'private SnapSession: один pointer edit.',
        'Capture/release: 5/8 dp → px → canvas.'
    ],'feature','ViewportController.kt')
    p.arrow([(334,785),(334,844)],color='feature',kind='own')
    p.arrow([(604,955),(684,955)],'snapshot','data',label_pos=(646,926))
    p.note(64,1120,1792,'Инварианты, которые удерживают поведение',[
        'CANCEL возвращает START без checkpoint. Save читает драфты до перехода на IO; обрезка не мутирует рабочий холст и историю.',
        'DraftMapperTest проверяет поля ItemDraft ↔ ItemSnap. Finalization/History-тесты проверяют неудачное сохранение и отмену.',
        'FitPreset.FIT/FILL — разовые операции; FramingMode.FIT — пересчитываемый режим. Это разные сущности и разные обязанности.'
    ],'core',150)

    p = Page('05-images','05 / Три маршрута изображений','Disk thumbnail, VM-local PhotoAssets и общий LruBitmapSource имеют разные ключи, владельцев и ограничения.','domain')
    p.card(64,240,540,242,'PhotoGrid → ThumbImage','COMPOSITION • remember(photoId)',[
        'Library и Builder PickPhotosDialog.',
        'repo.thumbPath(id) → thumbs/<id>.jpg',
        'LaunchedEffect → codec.decode() на IO.',
        'Bitmap хранится в remember ячейки.',
        'Этот маршрут обходит BitmapSource.'
    ],'ui','ThumbImage.kt')
    p.card(690,240,540,242,'PhotoAssets','PUBLIC CLASS • ОТДЕЛЬНО В КАЖДОЙ VM',[
        'Viewer / ComboList / Builder → new.',
        'ensureMeta(ids): PhotoRepository.get',
        'ensureBitmap(id, requiredPx): пропуск',
        'при достаточном loadedReqPx[id].',
        'StateFlow: photos и bitmaps.'
    ],'domain','PhotoAssets.kt')
    p.card(1316,240,540,242,'Политики загрузки экранов','VIEWMODEL • НЕ В КЭШЕ',[
        'ComboList: 800 px на фото превью.',
        'Viewer: 2048 px, окно index ± 1.',
        'Builder: maxBorder × viewport.zoom × 2,',
        'clamp 128…4096; debounce 300 ms.',
        'Export: размер по эффективному scale.'
    ],'feature','ComboBuilderViewModel.kt')
    p.arrow([(1316,370),(1236,370)],color='domain')
    p.arrow([(960,482),(960,547)],'BitmapSource.get','domain',label_pos=(960,526))
    p.card(690,547,1166,254,'LruBitmapSource : BitmapSource','KOIN SINGLE • PROCESS CONTAINER',[
        'PhotoDao.get → sampleSizeFor → key = photoId:sample → Mutex → cache / inFlight.',
        'Один Deferred на одинаковый ключ; разные ключи декодируются независимо.',
        'LinkedHashMap с переупорядочиванием при доступе; учёт width × height × 4 / 1024.',
        'maxCacheKb = max(4, JVM maxMemory / 1024 / 4). Это лимит только записей LRU.',
        'decodeScope = SupervisorJob + Dispatchers.Default; не дочерний scope ViewModel.'
    ],'domain','LruBitmapSource.kt')
    p.card(64,547,540,254,'evictExcept(keep)','PUBLIC METHOD • ВЫЗЫВАЕТ VIEWER',[
        'Удаляет bitmaps и loadedReqPx вне keep.',
        'Карта метаданных photos не очищается.',
        'ComboList и Builder eviction не вызывают.',
        'Ссылки VM переживают вытеснение из LRU.',
        'Общий лимит памяти не равен лимиту LRU.'
    ],'risk','PhotoAssets.kt')
    p.arrow([(1268,801),(1268,862)],color='core')
    p.card(690,862,1166,217,'PhotoStorage → ImageCodec → SkiaImageCodec','DISK → DECODE → ImageBitmap',[
        'photos/<fileName>: уже нормализованные при импорте пиксели; EXIF повторно не применяется.',
        'sample: степень двойки; уменьшенный decode или полный decode + scaleDown fallback.',
        'Файл прочитан в bytes. Временные и удерживаемые bitmap могут выйти за бюджет LRU.',
        'LruBitmapSource возвращает ImageBitmap; PhotoAssets публикует его в StateFlow.'
    ],'core','SkiaImageCodec.kt')
    p.note(64,862,540,'Осторожно с обещаниями lifecycle',[
        'Viewer создаёт новые launch при листании.',
        'Старый запрос может вернуть уже вытесненный id.',
        'PhotoAssets.runCatching ловит и отмену.',
        'Это риски по коду, не измеренные сбои.'
    ],'risk',217)
    p.note(64,1130,1792,'Владение ссылкой ≠ владение выделенной памятью',[
        'Один ImageBitmap может одновременно удерживаться LRU, PhotoAssets и UI. Удаление одной ссылки не доказывает освобождение изображения.',
        'У LruBitmapSource нет close()/явной отмены decodeScope. Не переносите предположение «экран закрыт → декод прекращён» в новый код.'
    ],'data',130)

    p = Page('06-storage','06 / Импорт, база и файлы','Repository API оперирует PhotoEntity / ComboEntity / ComboItemEntity / ComboWithItems из :data.','data')
    stages=[
        ('Выбор файлов','ImagePicker → ImageSource',['DesktopImagePicker: native dialog.','FileImageSource.openSource()','открывает новый поток на чтение.'],'DesktopImagePicker.kt','core'),
        ('Дедупликация','PhotoImporter • injected IO',['SHA-256 входного потока.','PhotoDao.findByHash(hash).','Дубликат пропускается.'],'PhotoImporter.kt','domain'),
        ('Копия и EXIF','PhotoStorage + ImageCodec',['Копирование в photos/<id>.<ext>.','Bounds → orientation → normalize.','Исходный выбранный файл не меняется.'],'PhotoStorage.kt','core'),
        ('Thumbnail и запись','PhotoDao.insert(PhotoEntity)',['Один sample → avgColor + JPEG thumb.','Строка БД добавляется после файлов.','Сбой: best-effort уборка копий.'],'PhotoImporter.kt','data'),
    ]
    for i,(title,tag,lines,src,color) in enumerate(stages):
        x=64+i*460
        p.card(x,240,412,236,title,tag,lines,color,src)
        if i<3: p.arrow([(x+412,355),(x+453,355)],color='data',kind='flow')
    p.card(64,550,540,279,'PhotoEntity → photos','ROOM ENTITY • PK id',[
        'fileName / originalName / mimeType',
        'width / height / sizeBytes',
        'sha256 UNIQUE / avgColor / importedAt',
        'PhotoDao.all(): importedAt DESC',
        'delete: precheck isPhotoUsed()',
        'FK RESTRICT защищает используемое фото.'
    ],'data','Entities.kt')
    p.card(690,550,540,279,'ComboItemEntity → combo_items','ROOM ENTITY • PK id',[
        'comboId → combos.id (CASCADE)',
        'photoId → photos.id (RESTRICT)',
        'zIndex / center / width / height',
        'rotation / framingMode: String',
        'contentScale / panX / panY',
        'Одна рамка содержит ровно одно фото.'
    ],'data','Entities.kt')
    p.card(1316,550,540,279,'ComboEntity → combos','ROOM ENTITY • PK id',[
        'name / canvasWidth / canvasHeight',
        'background: BackgroundSpec → JSON',
        'createdAt / updatedAt',
        'ComboWithItems: Embedded + Relation',
        'ComboDao.all(): updatedAt DESC',
        'Репозиторий сортирует items по zIndex.'
    ],'data','Daos.kt')
    p.arrow([(690,680),(610,680)],'N → 1','data',label_pos=(648,650))
    p.arrow([(1230,680),(1310,680)],'N → 1','data',label_pos=(1272,650))
    p.card(64,900,540,246,'AppDb / createAppDb()','ABSTRACT CLASS + DESKTOP FACTORY',[
        'Room schema v2; BundledSQLiteDriver.',
        'queries → Dispatchers.IO.',
        'MIGRATION_1_2: фото перенесено в item.',
        'При старых нескольких фото остаётся',
        'первое по orderIndex, затем id.'
    ],'data','DatabaseFactory.kt')
    p.card(690,900,540,246,'%APPDATA% / PhotoCombo','PERSISTENT • AppDirs',[
        'combo.db: конфигурация и метаданные.',
        'photos/: библиотечные копии.',
        'thumbs/: JPEG-миниатюры.',
        'gallery-settings.json: размер сеток.',
        'Файловые пути централизует PhotoStorage.'
    ],'data','DefaultAppDataDir.kt')
    p.card(1316,900,540,246,'Настройки галерей','GallerySettingsRepositoryImpl',[
        'Mutex сериализует load / saveScale.',
        'current — кеш в памяти; Photos / Combos.',
        'FileGallerySettingsStorage: .tmp',
        '→ atomicMove; диапазон 50…200%.',
        'Сбой диска: выбор остаётся в памяти.'
    ],'domain','GallerySettingsRepository.kt')
    p.note(64,1190,1792,'Формат данных — контракт совместимости',[
        'Имена FramingMode сохраняются через .name/valueOf. BackgroundSpec использует сериализованные типы auto/solid и id стратегии.',
        'Транзакция Room не охватывает файлы импорта. Удаление файлов best-effort; новая схема требует миграции и MigrationTest.'
    ],'risk',100)

    p = Page('07-render-export','07 / Один рендерер, три потребителя','Редактор работает с драфтом, список и PNG — с сохранённой конфигурацией. Общая геометрия обеспечивает согласованность.','core')
    p.card(64,240,540,201,'Builder / EditorCanvas','LIVE DRAFT',[
        'ItemDraft → draft.frameContent()',
        'CanvasTransform.ofViewport()',
        'Selection / Snap overlay рисуются отдельно.'
    ],'feature','EditorCanvas.kt')
    p.card(690,240,540,201,'ComboList / preview','SAVED CONFIG',[
        'ComboItemEntity → data.frameContent()',
        'CanvasTransform.fitInto()',
        'framesFor(): сортировка по zIndex.'
    ],'feature','ComboListScreen.kt')
    p.card(1316,240,540,201,'ComboPngExporter','SAVED CONFIG • KOIN SINGLE',[
        'ComboRepository.get(id) → snapshot.',
        'CanvasTransform(1f, Offset.Zero).',
        'ImageBitmap(canvasWidth, canvasHeight).'
    ],'domain','ComboPngExporter.kt')
    for x in (334,960,1586): p.line(x,441,x,470,'core')
    p.line(334,470,1586,470,'core')
    for x in (500,1420): p.arrow([(x,470),(x,495)],color='core',kind='flow')
    p.card(64,500,872,235,'buildItemFrame → contentArgsOf','PUBLIC FUNCTIONS • :core/render',[
        'Border + FramingMode + FrameContent → ItemFrame.',
        'effectiveFraming(FREE): stored scale/pan.',
        'effectiveFraming(FIT): fitScale, pan = 0.',
        'drawItem: clip рамки → avgColor fill → drawImage.',
        'rotation хранится, но текущий drawItem её не применяет.'
    ],'core','ItemFrames.kt')
    p.card(984,500,872,235,'comboBackgroundOf → BackgroundResolver','PUBLIC • STRATEGY',[
        'BackgroundSpec.Solid → цвет без вычисления.',
        'Auto → AverageAreaOklabStrategy → ResolvedBackground.Solid.',
        'Вес = площадь рамки в холсте; перекрытия не вычитаются.',
        'Oklab преобразует цвета; неизвестный id → default strategy.',
        'AverageColor вычисляется при импорте, а не каждый draw.'
    ],'core','Background.kt')
    p.arrow([(500,735),(500,790)],color='core',kind='flow')
    p.arrow([(1420,735),(1420,790)],color='core',kind='flow')
    p.card(64,790,1792,158,'DrawScope.drawCombo(transform, canvasW, canvasH, background, frames)','PUBLIC FUNCTION • SHARED RENDER PATH',[
        'translate(origin) → scale → clipRect(canvas) → drawComboBackground → frames.forEach(drawItem).',
        'Переданный порядок определяет слои. Overlay редактора не входит ни в превью, ни в экспорт.'
    ],'core','DrawCombo.kt')
    p.note(64,1008,872,'Save и Export — разные действия',[
        'Save: копия ComboSnap → trimmedToPhotos → Room-транзакция.',
        'Обрезка меняет размер сохранённого холста и центры его рамок.',
        'Export: читает уже сохранённый combo; не читает открытый draft.',
        'PNG имеет размер сохранённого холста, максимум 32 000 000 px.'
    ],'data',213)
    p.note(984,1008,872,'PngOutput → DesktopPngOutput',[
        'chooseDestination: Dispatchers.Swing / native FileDialog.',
        'Декод, render и write выполняются в IO-контексте exporter.',
        'Временный файл рядом с целью → move; уборка в finally.',
        'Экспорт внутрь каталога библиотеки запрещён самим адаптером.'
    ],'domain',213)
    p.text(64,1270,'Три независимых масштаба: разрешение холста • масштаб фото относительно оригинала • масштаб вида редактора.','body','core')

    p = Page('08-reuse','08 / Повторное использование и проверяемость','Общий код выделен по функции: универсальный UI, предметные галереи, редакторские правила и платформенные порты.','ui')
    p.card(64,240,540,306,':design-system','REUSABLE COMPOSE LIBRARY',[
        'components → primitives → theme → tokens',
        'icons независимы от предметной области.',
        'PcButton / IconButton / ActionGroup',
        'PcSelect / Slider / SegmentedControl',
        'PcTextField / Checkbox / Tooltip / Dialog',
        'PcOption<T>: значение / label / icon',
        'PcPlatformDialog: internal expect/actual'
    ],'ui','PcDialog.kt')
    p.card(690,240,540,306,':ui-common','SHARED APPLICATION UI',[
        'GalleryGrid<T>: колонки и размеры ячеек.',
        'PhotoGrid: PhotoEntity + ThumbImage.',
        'GallerySizeControl + GalleryScaleState.',
        'BackDispatcher / RegisterBackHandler.',
        'BackKeyHost / KeyInputHost / BackNavIcon.',
        'CollectMessages: Flow → Snackbar.',
        'Знает domain/data; это не независимая DS.'
    ],'ui','PhotoGrid.kt')
    p.card(1316,240,540,306,':core и :domain','REUSE WITH DIFFERENT COUPLING',[
        'core.render: общая геометрия и draw.',
        'core.model: FramingMode без app imports.',
        'core.image/platform: заменяемые порты.',
        'domain: PhotoAssets без Koin.',
        'PhotoImporter: injected clock/id/IO.',
        'ComboPngExporter: injected PngOutput.',
        'domain связан с Room-сущностями data.'
    ],'core','Repositories.kt')
    p.text(64,612,'Что переиспользуется между фичами','body','ui')
    reuse=[
        ('Library + Builder picker','PhotoGrid → GalleryGrid<T> → ThumbImage'),
        ('Library + ComboList','GalleryScaleState / GallerySizeControl'),
        ('Viewer + ComboList + Builder','PhotoAssets → BitmapSource'),
        ('Builder + ComboList + PNG export','buildItemFrame / effectiveFraming / drawCombo'),
        ('Все экраны','PcTheme / Pc* + back/input helpers'),
    ]
    for i,(a,b) in enumerate(reuse):
        yy=648+i*57; p.rect(64,yy,1792,47,'#122239',rx=9)
        p.text(84,yy+31,a,'body','feature'); p.text(700,yy+31,b,'mono')
    p.card(64,979,540,287,'test-support','TEST-ONLY MODULE',[
        'FakePhotoDao / FakeComboDao',
        'FakeLogger / CountingCodec',
        'FakePhotoRepository / FakeComboRepository',
        'StubBitmapSource / FakeImagePicker',
        'ImageFixtures / UiTestBase / TestBackHost',
        'Тесты используют временные каталоги.'
    ],'test','test-support/build.gradle.kts')
    p.card(690,979,540,287,'Архитектурные проверки','SOURCE + MODULE GUARDS',[
        'verifyModuleGraph: точный Gradle-граф.',
        'ArchitectureRulesTest: imports / cycles.',
        'SourceHygieneTest: UTF-8 / LF / BOM.',
        'AppModulesTest: сборка графа Koin.',
        'AppNavViewModelTest: owner scope.',
        'Набор тестов не равен формальному proof.'
    ],'test','build.gradle.kts')
    p.card(1316,979,540,287,'Контракты поведения','TESTS AS READING MAP',[
        'ItemFrameParityTest / DrawCombo*Test',
        'DraftMapperTest / DraftHistoryTest',
        'ComboBuilderFinalizationTest',
        'LruBitmapSourceTest / PhotoImporterTest',
        'ComboPngExporterTest / MigrationTest',
        'DesignSystem / Gallery / Gestures tests'
    ],'test','docs/DEVELOPMENT.md')

    p = Page('09-lifetimes','09 / Кто владеет состоянием','Фактические точки создания и очистки. «Должен жить» и «код явно освобождает» показаны раздельно.','app')
    rows=[
        ('Koin container','single-сервисы, AppDb, LRU','startKoin(main)','AppDb.onClose → close; stopKoin в main нет.','app'),
        ('AppNav composition','rootOwner, backStack','remember в AppNav','rootOwner: нет DisposableEffect с clear().','app'),
        ('Корневые вкладки','LibraryVM / ComboListVM','koinViewModel + rootOwner','Живут при переключениях, пока удержан owner.','feature'),
        ('EntryScope composition','ViewerVM / BuilderVM','rememberEntry…Owner','onDispose → ViewModelStore.clear().','feature'),
        ('ViewModel scope','jobs, PhotoAssets, draft/history','init / constructor VM','Отмена scope при clear; ссылки живут с VM.','domain'),
        ('LRU decodeScope','Deferred + общий кэш','SupervisorJob + Default','Не дочерний к VM; явного close/cancel нет.','domain'),
        ('Compose instance','dialogs, pager, Thumb bitmap','remember / LaunchedEffect','Эффекты привязаны к композиции / ключам.','ui'),
        ('Один жест','pending ComboSnap / SnapSession','START / ItemPressed','END: checkpoint; CANCEL: restore + clear.','core'),
        ('Одна операция Save/Export','снимок / Bitmap / временный файл','save() / export(id)','Снимок локальный; временные файлы в finally.','data'),
        ('Перезапуск приложения','Room + photos + thumbs + JSON','файлы в AppDirs','Долговечные данные; Undo / backStack не пишутся.','data'),
    ]
    headers=[(80,'ОБЛАСТЬ'),(450,'СОДЕРЖИМОЕ'),(970,'СОЗДАНИЕ'),(1390,'ЗАВЕРШЕНИЕ / ГРАНИЦА')]
    for x,t in headers:p.text(x,258,t,'label','muted')
    for i,(scope,contents,create,end,color) in enumerate(rows):
        y=282+i*77
        p.rect(64,y,1792,67,'#122239',rx=10)
        p.rect(64,y+12,4,43,COL[color],rx=2)
        p.lines(80,y+27,[scope],30,'small',color,24)
        p.lines(450,y+27,[contents],43,'small',step=24)
        p.lines(970,y+27,[create],33,'small',step=24)
        p.lines(1390,y+27,[end],42,'small',step=24)
    p.note(64,1090,872,'Границы доступа в Kotlin-коде',[
        'public — по умолчанию; internal — модуль, включая соседние пакеты.',
        'private: член виден внутри класса; top-level — внутри файла.',
        'DraftEditor/History/Snap internal. ItemDraft публичен и изменяем.',
        'FrameSnap public, constructor private; FrameSnapState ctor internal.'
    ],'app',184)
    p.note(984,1090,872,'Риски по чтению кода: проверить при расширении',[
        'Кэш VM удерживает bitmap после LRU eviction; Builder/List без pruning.',
        'Viewer launch может завершиться после смены окна index ± 1.',
        'PhotoAssets.runCatching / PhotoImporter.catch(Exception) ловят отмену.',
        'Это список проверяемых гипотез; runtime-профилирование не проводилось.'
    ],'risk',184)

def make_catalog():
    ordered = sorted(FILES,key=lambda f:(MODULES.index(f['module']),f['sourceSet'],f['path']))
    for start in range(0,len(ordered),32):
        batch=ordered[start:start+32]
        number=start//32+1
        p=Page(f'10-catalog-{number}',f'Каталог компонентов / {number:02d}',
               'Каждая карточка — один Main-файл. P = public по умолчанию; I = internal; V = private. Считаются top-level объявления.','test')
        for i,f in enumerate(batch):
            x=64+(i%4)*460; y=246+(i//4)*125
            p.s.append(f'<a href="{href(f["path"])}" target="_blank"><g class="card"><title>{esc(f["path"])}</title>')
            color='test' if f['module']==':test-support' else ('feature' if f['module'].startswith(':feature') else {'composeApp':'app','domain':'domain','data':'data','core':'core','design-system':'ui','ui-common':'ui'}[f['module'][1:]])
            p.rect(x,y,412,111,'#122239',COL[color],rx=12,extra='class="card-bg" stroke-opacity=".4"')
            p.text(x+16,y+25,f['module']+' / '+('desktop' if f['sourceSet']=='desktopMain' else 'common'),'small',color,size=16)
            p.text(x+16,y+57,f['name'],'body',size=20)
            counts={v:sum(d['visibility']==v for d in f['declarations']) for v in ('public','internal','private')}
            p.text(x+16,y+89,f'P {counts["public"]}  /  I {counts["internal"]}  /  V {counts["private"]}     ↗ исходник','small')
            p.s.append('</g></a>')
        p.text(64,1280,f'Файлы {start+1}…{start+len(batch)} из {len(ordered)}. Полные объявления и внутренние импорты: SOURCE_INDEX.md.','small')

def make_inventory():
    text=['# PhotoCombo — полный индекс исходников','',
          f'Снимок `{REV}`, {STAMP}. **{len(FILES)} Main-файлов**, включая `test-support`; **{len(TESTS)} файлов тестов**.','',
          'Этот индекс автоматически читает весь `src/commonMain` и `src/desktopMain` зарегистрированных модулей. '
          'Объявления извлекаются по началу строки, а не Kotlin-компилятором: здесь показан top-level API, '
          'включая константы и private helpers. Члены классов и вложенные типы изучайте по ссылке на исходник. '
          'Импорт — зависимость имени, не доказательство вызова. Использования в том же пакете не требуют import.','',
          '[Открыть атлас](open-atlas.html) · [Onboarding](ONBOARDING.md)','']
    for module in MODULES:
        text += ['## '+module,'']
        for f in (f for f in FILES if f['module']==module):
            text += [f'### {f["name"]}', '', f'[{f["path"]}]({href(f["path"])})', '',
                     f'`{f["package"]}` · `{f["sourceSet"]}`','', '```kotlin']
            text += [f'// L{d["line"]} · {d["visibility"]}\n{d["text"]}' for d in f['declarations']]
            text += ['```','']
            if f['imports']:
                text += ['<details><summary>Внутренние импорты</summary>','','```kotlin']
                text += ['import '+imp for imp in f['imports']]
                text += ['```','','</details>','']
    text += ['## Тесты: полный список файлов','','Наличие теста не означает, что он был запущен при создании атласа.','']
    for f in TESTS: text += [f'- [{Path(f).name}](../../{quote(f,safe="/")}) — `{f.split("/src/")[0]}`']
    write(OUT/'SOURCE_INDEX.md','\n'.join(text)+'\n')
    write(OUT/'source-manifest.json',json.dumps({'sourceCommit':REV,'date':STAMP,'files':FILES,'tests':TESTS},ensure_ascii=False,indent=2)+'\n')

def make_viewer():
    entries=[{'file':p.slug+'.svg','title':p.title,'subtitle':p.subtitle,
              'svg':svg_doc(p.render(),H,p.title).split('\n',1)[1]} for p in PAGES]
    html='''<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>PhotoCombo — архитектурный атлас</title>
<style>
:root{color-scheme:dark;font:15px/1.5 "Segoe UI",Arial,sans-serif;background:#0b1526;color:#eef3fb}
*{box-sizing:border-box}body{margin:0}button,input,a{font:inherit}button,a{color:inherit}
header{padding:18px 24px;display:flex;gap:18px;align-items:center;flex-wrap:wrap;background:#122239;border-bottom:1px solid #30445d}
h1{font-size:20px;margin:0;letter-spacing:-.3px}header small{color:#a4b4cc}main{display:grid;grid-template-columns:288px minmax(0,1fr);height:calc(100vh - 80px)}
nav{overflow:auto;padding:18px;border-right:1px solid #30445d}nav button{display:block;width:100%;text-align:left;padding:12px;margin-bottom:7px;border:1px solid transparent;border-radius:10px;background:transparent;color:#a4b4cc;cursor:pointer}
nav button:hover,nav button[aria-current=true]{background:#192f48;border-color:#56789d;color:#fff}nav button[hidden]{display:none}input{width:100%;padding:10px;margin-bottom:16px;border:1px solid #3d5674;border-radius:8px;background:#101e32;color:inherit}
.workspace{min-width:0;display:flex;flex-direction:column}.toolbar{display:flex;align-items:center;gap:9px;flex-wrap:wrap;padding:12px 18px;border-bottom:1px solid #30445d}
.toolbar button,.toolbar a{background:#172b43;border:1px solid #3a536f;border-radius:8px;padding:7px 12px;text-decoration:none;cursor:pointer}.toolbar label{margin-left:8px}.toolbar input{width:auto;margin:0;accent-color:#57ddc4}
#viewport{flex:1;overflow:auto;overscroll-behavior:contain;background:#070f1b;position:relative}#sheet{display:block;border:0;margin:0 auto;min-width:320px}#sheet>svg{display:block;width:100%;height:auto}#status{color:#a4b4cc;margin-left:auto}.hint{padding:8px 18px;color:#a4b4cc;font-size:13px;border-top:1px solid #30445d}a:focus-visible,button:focus-visible,input:focus-visible{outline:2px solid #57ddc4;outline-offset:2px}
@media(max-width:850px){header{padding:12px}main{height:auto;display:block}nav{padding:10px;overflow-x:auto;border-bottom:1px solid #30445d}#chapters{display:flex;gap:8px}nav input{display:none}nav button{min-width:190px;margin:0}#viewport{height:75vh}.toolbar{padding:10px}h1{font-size:18px}}
</style><header><h1>PhotoCombo / архитектурный атлас</h1><small>Исходники SOURCE_REV · 14 ноября 2026</small><a href="ONBOARDING.html">Onboarding</a><a href="SOURCE_INDEX.md">Все исходники</a></header>
<main><nav aria-label="Главы"><input id="search" placeholder="Найти главу…" aria-label="Найти главу"><div id="chapters"></div></nav>
<section class="workspace"><div class="toolbar"><button id="fit">Вписать</button><button id="minus" aria-label="Уменьшить масштаб">−</button><button id="plus" aria-label="Увеличить масштаб">+</button><button id="actual">100%</button><a id="download" download>SVG главы ↓</a><a href="PhotoCombo-Architecture.svg" download>Весь атлас ↓</a><label><input type="checkbox" id="motion"> Анимация</label><span id="status" aria-live="polite"></span></div>
<div id="viewport"><div id="sheet" aria-label="Архитектурная схема"></div></div>
<div class="hint">Прокрутка — по схеме. + / − меняют масштаб; 0 вписывает. ← / → переключают главы. Карточки ↗ открывают исходники. SVG работает и отдельно от этого просмотрщика.</div></section></main>
<script>
const chapters=ENTRIES;let current=0,scale=1,fit=true;
const sheet=document.querySelector('#sheet'),viewport=document.querySelector('#viewport'),status=document.querySelector('#status');
const motion=document.querySelector('#motion');motion.checked=!matchMedia('(prefers-reduced-motion: reduce)').matches;
function applyMotion(){const svg=sheet.querySelector('svg');if(!svg)return;let style=svg.querySelector('#viewer-motion');if(!style){style=document.createElementNS('http://www.w3.org/2000/svg','style');style.id='viewer-motion';svg.append(style)}style.textContent=motion.checked?'':'.flow{animation:none!important}'}
function resize(){if(fit)scale=Math.max(.2,(viewport.clientWidth-20)/1920);sheet.style.width=(1920*scale)+'px';sheet.style.height=(1380*scale)+'px';status.textContent=Math.round(scale*100)+'%'}
function select(i){current=(i+chapters.length)%chapters.length;const c=chapters[current];sheet.dataset.file=c.file;sheet.innerHTML=c.svg;document.querySelector('#download').href=c.file;document.querySelectorAll('nav button').forEach((b,j)=>b.setAttribute('aria-current',j===current));viewport.scrollTo(0,0);location.hash=String(current);resize();applyMotion();sheet.querySelectorAll('a').forEach(a=>{const target=chapters.findIndex(c=>c.file===a.getAttribute('href'));if(target>=0)a.onclick=e=>{e.preventDefault();select(target)}})}
chapters.forEach((c,i)=>{let b=document.createElement('button');b.textContent=c.title;b.title=c.subtitle;b.onclick=()=>select(i);document.querySelector('#chapters').append(b)});
document.querySelector('#fit').onclick=()=>{fit=true;resize()};document.querySelector('#actual').onclick=()=>{fit=false;scale=1;resize()};
function zoom(factor){fit=false;scale=Math.max(.2,Math.min(3,scale*factor));resize()}
document.querySelector('#plus').onclick=()=>zoom(1.25);document.querySelector('#minus').onclick=()=>zoom(.8);motion.onchange=applyMotion;sheet.onload=applyMotion;
document.querySelector('#search').oninput=e=>{const q=e.target.value.toLowerCase();document.querySelectorAll('nav button').forEach((b,i)=>b.hidden=!(chapters[i].title+' '+chapters[i].subtitle).toLowerCase().includes(q))};
document.addEventListener('keydown',e=>{if(e.target.matches('input'))return;if(e.key==='+')zoom(1.25);if(e.key==='-')zoom(.8);if(e.key==='0'){fit=true;resize()}if(e.key==='ArrowRight')select(current+1);if(e.key==='ArrowLeft')select(current-1)});window.addEventListener('resize',resize);select(Number(location.hash.slice(1))||0);
</script></html>'''
    html=html.replace('SOURCE_REV',REV[:12]).replace('ENTRIES',json.dumps(entries,ensure_ascii=False))
    write(OUT/'open-atlas.html',html)

def main():
    make_pages()
    make_catalog()
    for p in PAGES: write(OUT/(p.slug+'.svg'),svg_doc(p.render(),H,p.title))
    master=''.join(f'<g id="chapter-{i}" transform="translate(0 {i*H})">{p.render()}</g>' for i,p in enumerate(PAGES))
    write(OUT/'PhotoCombo-Architecture.svg',svg_doc(master,H*len(PAGES),'PhotoCombo — полный архитектурный атлас'))
    make_inventory()
    make_viewer()
    print(json.dumps({'pages':len(PAGES),'mainFiles':len(FILES),'testFiles':len(TESTS),'sourceCommit':REV},ensure_ascii=False))

if __name__=='__main__':
    main()

