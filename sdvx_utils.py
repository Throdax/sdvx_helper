def get_version(app:str) -> str:        
    
    SWVER = None
    
    with open('version.properties', 'r') as f:
        line = f.readline()
        
        while line != "" :
            if line.split("=")[0].strip() == app :
                SWVER = line.split("=")[1].strip()
            line = f.readline()
        
        if SWVER is None:
            raise ValueError
        
        return SWVER
    
    
        
    