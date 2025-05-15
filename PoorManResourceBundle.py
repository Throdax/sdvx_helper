import os

bundles = {}


class PoorManResourceBundle :
    bundle = None
    
    def __init__(self, locale:str='ja'):
        
        self.load_bundles()
        self.bundle = bundles.get(locale.lower());
        

    def load_bundle(self, bundle_file, locale):
        print(f'Loading bundle "{locale}" from file "{bundle_file}"...')
        
        with open('resources/i18n/' + bundle_file, 'r',encoding="utf-8") as f:
            locale_bundle = {}
            for line in f.readlines():
                key = line[:line.find("=")].strip()
                value = line[line.find("=") + 1:].strip()
                locale_bundle[key] = value
            
            bundles[locale] = locale_bundle
            
        print(f'Bundle "{locale}" loaded.')

    def load_bundles(self):
        bundle_files = os.listdir('resources/i18n')
        for bundle_file in bundle_files :
            locale = bundle_file[-13:-11]
            self.loadBundle(bundle_file, locale)
        
        
    def get_text(self, key: str):
        if self.bundle != None :
            return self.bundle.get(key)
        else :
            raise RuntimeError('Bundle not initialized')

    def get_text(self, key: str, *args):
        message = getText(key)
        for i, replacement in enumerate(args) :
            token = '{'+i+'}'
            if token in message :
                message = message.replace(token,replacement)
            else :
                raise ValueError('More arguments supplied than available tokens on message')

        return message

    def get_available_bundles(self):
        return list(bundles.keys())
        


if __name__ == '__main__':
    a = PoorManResourceBundle(locale = 'ja')        
        
        
