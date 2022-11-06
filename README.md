# EpsonPlata

Проект предполагает взаимодействие с аппаратурой Epson очками модели bt-035e.
НО! Хочется отметить, что очки представляют из себя:
* каркас
* на линзы производится проекция экрана
* а на душке находится 5-ти мегапиксельная камера
Нет ничего сверхнеобычного и всё может быть заменено на другие аналоги по необходимости. В данном случае работа с очками была из-за доступности.
Также параллельно создавалось приложение для телефона ([работа с встроенной камерой](https://github.com/MrApple100/DetectionObjectDeviceCamera.git)) с тем же функционалом, что и для очков. Это облегчало тестирование и логирование.

Функционал:
1) Нахождение элементов с использованием искусственного интеллекта.
2) Подсказка при наведении центральной точки на элемент
3) Сканирование QR-кодов и его аналогов.
4) Высвечивание информации после сканирования QR-кода.
5) Управление пролистыванием стриниц с помощью свайпа руки перед камерой.

[Ссылка на демонстрацию работоспобности](https://disk.yandex.ru/i/E1mCz8ZUZmvwRQ) 

![штрихкод](https://user-images.githubusercontent.com/69810254/200172383-2aa5f601-701e-4ab9-a401-088fdd19987d.jpg)


Настройка:
Official android object detection [guide](https://www.tensorflow.org/lite/models/object_detection/overview)
 rewritten using kotlin, CameraX and with more readable code

[Video](https://www.youtube.com/watch?v=GXtiLAjPlHg)

Полный путь от обучения модели до внедрения ИИ в андроид приложение
[Статья на habr](https://habr.com/ru/company/redmadrobot/blog/488210/)

Перед обучением прочитайте замечания по статье, чтобы легче было исправлять ошибки.

### Замечания по статье на 21.06.2022:

* 
> *После этого можно приступить к разметке данных (это самый долгий и скучный этап):*
> 
> python labelImg.py

Запуск этой строчки происходит с ошибкой, чтобы от нее избавиться, нужно заменить deprecated элементы на указанные.

*
После подготовик данных предлагают использовать подготовленную модель

>Доступные модели для переобучения можно найти [тут](https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/detection_model_zoo.md).
>
>Сейчас мы выберем модель ssdlite_mobilenet_v2_coco, чтобы в дальнейшем запустить обученную модель на android устройстве.

Модель представленная там происходит, в будущем запуститься с ошибкой.

Используется эта [модель](https://github.com/tensorflow/models/blob/master/research/object_detection/samples/configs/ssdlite_mobilenet_v2_coco.config)

Изменяйте эту модель в дальнейшем.

*
Сейчас ссылка на файл в диске выглядит по другому

Как это в статье:

*drive.google.com/open?id=[YOUR_FILE_ID_HERE]*

Как это сейчас:

*drive.google.com/file/d/[YOUR_FILE_ID_HERE]/view?usp=sharing*

*
Когда ужу вы перейдете в colab, используйте [этот](https://colab.research.google.com/drive/1caxlJWjvR11zh1deD9SB3ntdJ1IKIH2B#scrollTo=3MGgu9GNfCow) код, а не представленный там. Дополнен нужными библиотеками.

>Запускаем процесс обучение, где:
>
>!python ./models/research/object_detection/legacy/train.py --logtostderr --train_dir=./training_demo/training --pipeline_config_path=./training_demo/training/ssdlite_mobilenet_v2_coco.config

Этот код запуститься с ошибкой и исправление ее вы найдете по [ссылке](https://github.com/tensorflow/models/issues/9706)

*
Также наблюдайте чтобы процесс обучения не остановился. Он может.

