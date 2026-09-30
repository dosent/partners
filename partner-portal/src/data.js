export const initialLeads = [
 { id: 'LD-1024', company: 'Альфа Логистика', request: 'Java-команда', status: 'Подбор', date: '22 сент. 2026', domain: 'alfa-logistics.example', budget: '1 200 000', description: 'Команда из двух Java-разработчиков для развития логистической платформы.', contact: 'Ирина, руководитель ИТ', email: 'irina@example.com' },
 { id: 'LD-1023', company: 'Волга Ритейл', request: 'Frontend-разработка', status: 'Квалификация', date: '21 сент. 2026', domain: 'volga-retail.example', budget: '800 000', description: 'Развитие личного кабинета и интернет-магазина на React.', contact: 'Михаил, CTO', email: 'mikhail@example.com' },
 { id: 'LD-1022', company: 'ТехноСфера', request: 'QA-инженеры', status: 'Согласование', date: '20 сент. 2026', domain: 'technosphere.example', budget: '650 000', description: 'Автоматизация регрессионного тестирования.', contact: 'Ольга, руководитель разработки', email: 'olga@example.com' },
 { id: 'LD-1021', company: 'Орион', request: 'Аналитик', status: 'Новый', date: '19 сент. 2026', domain: 'orion.example', budget: '400 000', description: 'Системный аналитик для нового продукта.', contact: 'Андрей, директор', email: 'andrey@example.com' },
 { id: 'LD-1020', company: 'Меридиан', request: 'Java-разработка', status: 'Квалификация', date: '18 сент. 2026', domain: 'meridian.example', budget: '720 000', description: 'Развитие внутренних сервисов.', contact: 'Анна', email: 'anna@example.com' },
 { id: 'LD-1019', company: 'Вектор', request: 'DevOps', status: 'Подбор', date: '17 сент. 2026', domain: 'vector.example', budget: '560 000', description: 'Развитие инфраструктуры.', contact: 'Денис', email: 'denis@example.com' },
 { id: 'LD-1018', company: 'Сфера', request: 'Backend-команда', status: 'Согласование', date: '16 сент. 2026', domain: 'sphere.example', budget: '900 000', description: 'Разработка API.', contact: 'Елена', email: 'elena@example.com' },
];
export const initialVacancies = [
 {id:'SV-041',company:'Альфа Логистика',role:'Java-разработчик',grade:'Senior',stack:'Java, Spring, PostgreSQL',hours:'40',format:'Удалённо',deadline:'2026-09-30', candidates:[{id:'SS-042',experience:'7 лет',skills:'Java · Spring Boot · PostgreSQL',description:'Разработка высоконагруженных сервисов. Опыт в финтехе и электронной коммерции.',status:'На рассмотрении'},{id:'SS-058',experience:'5 лет',skills:'Java · Kafka · Docker',description:'Микросервисные системы, интеграции и обработка событий.',status:'На рассмотрении'}]},
 {id:'SV-040',company:'ТехноСфера',role:'QA-инженер',grade:'Middle',stack:'Java, Selenium',hours:'40',format:'Удалённо',deadline:'2026-10-05',candidates:[]}
];
export const candidatePool = [
 {id:'SS-063',experience:'6 лет',skills:'Java · Spring · Kafka · PostgreSQL',description:'Разрабатывал микросервисную платформу и интеграции для крупного ритейла.',status:'Доступен'},
 {id:'SS-071',experience:'4 года',skills:'React · TypeScript · Next.js',description:'Создавал личные кабинеты и интерфейсы для высоконагруженных веб-продуктов.',status:'Доступен'},
 {id:'SS-077',experience:'8 лет',skills:'QA · Selenium · Java · CI/CD',description:'Настраивал автоматизацию тестирования и процессы контроля качества.',status:'Доступен'},
 {id:'SS-081',experience:'5 лет',skills:'Java · Spring Boot · Docker',description:'Разрабатывал серверные приложения и интеграционные сервисы.',status:'Доступен'},
];
export const payments = [
 {project:'Альфа Логистика',period:'Август 2026',revenue:3200000,rate:10,amount:320000,status:'К выплате'},
 {project:'Волга Ритейл',period:'Август 2026',revenue:2600000,rate:10,amount:260000,status:'Выплачено'},
 {project:'ТехноСфера',period:'Август 2026',revenue:2600000,rate:10,amount:260000,status:'Выплачено'}
];
export const money = n => new Intl.NumberFormat('ru-RU').format(n) + ' ₽';
export const statusClass = s => ({'Подбор':'blue','Квалификация':'amber','Согласование':'violet','Новый':'green','Выплачено':'green','К выплате':'blue','Интервью':'blue','Оффер':'green','Отказ':'red','На рассмотрении':'amber','Доступен':'green','Ожидает подтверждения SimbirSoft':'amber','Подтверждение SimbirSoft':'amber'}[s] || 'blue');
export function downloadFile(name,content,type='text/plain;charset=utf-8') { const url=URL.createObjectURL(new Blob(['\uFEFF',content],{type})); const a=document.createElement('a'); a.href=url; a.download=name; a.click(); setTimeout(()=>URL.revokeObjectURL(url),1000); }
