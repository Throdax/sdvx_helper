def get_version(app:str) -> str:        
    with open('version.properties', 'r') as f:
        line = f.readline()
        
        while line != "" :
            if line.split("=")[0].strip() == "helper" :
                SWVER = line.split("=")[1].strip()
            line = f.readline()
        
        if SWVER is None:
            raise ValueError
        
        return SWVER
    
    
        
    