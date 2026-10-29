package br.com.example.activation.service;
import br.com.example.activation.api.CreateActivationRequest;
import br.com.example.activation.domain.*;
import br.com.example.activation.integration.*;
import br.com.example.activation.persistence.ActivationRequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service
public class ActivationRequestService {
 private final ActivationRequestRepository repo; private final ProviderActivationClient provider;
 public ActivationRequestService(ActivationRequestRepository r,ProviderActivationClient p){repo=r;provider=p;}
 public ActivationRequest create(CreateActivationRequest in){
  var old=repo.findByExternalId(in.externalId());
  if(old.isPresent()) return same(old.get(),in)?old.get():conflict();
  ActivationRequest saved;
  try { saved=repo.saveAndFlush(ActivationRequest.create(in.externalId(),in.customerId(),in.productCode())); }
  catch(DataIntegrityViolationException e){
   var concurrent=repo.findByExternalId(in.externalId());
   if(concurrent.isPresent()&&same(concurrent.get(),in)) return concurrent.get();
   if(concurrent.isPresent()) return conflict();
   throw e;
  }
  return process(saved.getId());
 }
 @Transactional public ActivationRequest process(UUID id){
  var r=repo.findById(id).orElseThrow(()->new ActivationRequestNotFoundException(id));
  r.markProcessing(); r=repo.saveAndFlush(r);
  try {provider.activate(r.getExternalId(),r.getCustomerId(),r.getProductCode());r.markCompleted();}
  catch(ProviderActivationException e){r.markFailed();}
  return repo.save(r);
 }
 @Transactional(readOnly=true) public ActivationRequest get(UUID id){return repo.findById(id).orElseThrow(()->new ActivationRequestNotFoundException(id));}
 @Transactional(readOnly=true) public Page<ActivationRequest> list(ActivationStatus s,String c,Pageable p){
  if(s!=null&&c!=null)return repo.findByStatusAndCustomerId(s,c,p);
  if(s!=null)return repo.findByStatus(s,p); if(c!=null)return repo.findByCustomerId(c,p); return repo.findAll(p);
 }
 private boolean same(ActivationRequest r,CreateActivationRequest i){return r.getCustomerId().equals(i.customerId())&&r.getProductCode().equals(i.productCode());}
 private ActivationRequest conflict(){throw new DuplicateExternalIdException("externalId já utilizado com conteúdo diferente.");}
}
