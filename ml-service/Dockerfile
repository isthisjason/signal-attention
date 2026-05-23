FROM python:3.12-slim

WORKDIR /app

ARG INSTALL_TORCH=false

COPY requirements.txt requirements-torch.txt ./
RUN pip install --no-cache-dir -r requirements.txt \
    && if [ "$INSTALL_TORCH" = "true" ]; then pip install --no-cache-dir -r requirements-torch.txt; fi

COPY app ./app
COPY scripts ./scripts

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
