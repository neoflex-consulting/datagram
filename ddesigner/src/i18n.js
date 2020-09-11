import i18n from 'i18next';
//import XHR from 'i18next-xhr-backend';
//import Cache from 'i18next-localstorage-cache';
import LanguageDetector from 'i18next-browser-languagedetector';
import ru_common from './locales/ru/common.json'
import en_common from './locales/en/common.json'
import ru_links from './locales/ru/links.json'
import ru_modules from './locales/ru/modules.json'
import ru_classes from './locales/ru/classes.json'
import ru_references from './locales/ru/references.json'
import ru_views from './locales/ru/views.json'
import en_links from './locales/en/links.json'
import en_modules from './locales/en/modules.json'
import en_classes from './locales/en/classes.json'
import en_references from './locales/en/references.json'
import en_views from './locales/en/views.json'

i18n
    //.use(XHR)
    // .use(Cache)
    .use(LanguageDetector)
    .init({
        resources: {
            "ru-RU": {
                "links": ru_links, 
                "modules": ru_modules, 
                "classes": ru_classes, 
                "common": ru_common,
                "references": ru_references,
                "views": ru_views
            },            
            "en-EN": {
                "links": en_links, 
                "modules": en_modules, 
                "classes": en_classes, 
                "common": en_common,
                "references": en_references,
                "views": en_views
            }            
        },
        fallbackLng: 'en-EN',

        // have a common namespace used around the full app
        ns: ['common'],
        defaultNS: 'common',

        debug: true,

        // cache: {
        //   enabled: true
        // },

        interpolation: {
            //escapeValue: false, // not needed for react!!
            formatSeparator: ',',
            format: function(value, format, lng) {
                if (format === 'uppercase') return value.toUpperCase();
                return value;
            }
        }
    });


export default i18n
