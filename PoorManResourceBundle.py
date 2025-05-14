import os

bundles = {}


class PoorManResourceBundle :
    bundle = None
    
    def __init__(self, locale:str='ja'):
        
        self.loadBundles()
        self.bundle = bundles.get(locale.lower());
        

    def loadBundle(self, bundleFile, locale):
        print(f'Loading bundle "{locale}" from file "{bundleFile}"...')
        
        with open('resources/i18n/' + bundleFile, 'r',encoding="utf-8") as f:
            localeBundle = {}
            for line in f.readlines():
                key = line[:line.find("=")].strip()
                value = line[line.find("=") + 1:].strip()
                localeBundle[key] = value
            
            bundles[locale] = localeBundle
            
        print(f'Bundle "{locale}" loaded.')

    def loadBundles(self):
        bundleFiles = os.listdir('resources/i18n')
        for bundleFile in bundleFiles :
            locale = bundleFile[-13:-11]
            self.loadBundle(bundleFile, locale)
        
        
    def getText(self, key):
        if not self.bundle == None :
            return self.bundle.get(key)
        else :
            raise 'Bundle not initialised'


if __name__ == '__main__':
    a = PoorManResourceBundle(locale = 'ja')        
        
        