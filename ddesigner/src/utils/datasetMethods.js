import _ from 'lodash';
import resource from "./../Resource";

function getLinkedEntity(parentEntity, workspaceForLink, callBack){
    resource.getEntity(parentEntity._type_, parentEntity.e_id).then((result)=>{
        if(result){
            const entity = {
                shortName: parentEntity.shortName,
                name: `${workspaceForLink.name}_${parentEntity.shortName}`,
                _type_: "sse.LinkedDataset",
                linkTo: parentEntity,
                workspace: workspaceForLink,
                columns: copyParentColumns(result.columns)
            }
            callBack(entity)
        }        
    })
}

function copyParentColumns(columns){
    if(columns.length > 0){
        const columnsCopy = _.cloneDeepWith(columns, a => {
            if(a && a.e_id){
                a['e_id'] = undefined
            }
        })
        return columnsCopy
    }
    return []
}

export {getLinkedEntity}